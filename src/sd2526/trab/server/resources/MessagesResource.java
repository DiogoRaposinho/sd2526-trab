package sd2526.trab.server.resources;

import jakarta.inject.Singleton;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;
import sd2526.trab.api.Message;
import sd2526.trab.api.User;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.rest.RestMessages;
import sd2526.trab.clients.rest.RestUsersClient;
import sd2526.trab.server.persistence.Hibernate;
import java.io.IOException;
import java.net.URI;
import java.util.*;

@Singleton
@Path("/messages")
public class MessagesResource implements RestMessages {
  private final String domain;
  private final Hibernate hibernate = Hibernate.getInstance();
  private final Discovery discovery = new Discovery();
  private final Client client = ClientBuilder.newClient();

  public MessagesResource(String domain) throws IOException {
    this.domain = domain;
    this.discovery.start();
  }

  private User validate(String name, String pwd) {
    URI[] uris = discovery.knownUrisOf("Users@" + domain, 1);
    if (uris == null || uris.length == 0)
      throw new WebApplicationException(Status.SERVICE_UNAVAILABLE);
    for (URI uri : uris) {
      if ("http".equalsIgnoreCase(uri.getScheme())) {
        Result<User> res = new RestUsersClient(uri).getUser(name, pwd);
        if (res == null || !res.isOK())
          throw new WebApplicationException(Status.FORBIDDEN);
        return res.value();
      }
    }
    throw new WebApplicationException(Status.SERVICE_UNAVAILABLE);
  }

  private boolean checkUserExistsRemotely(String userName) {
    try {
      URI[] uris = discovery.knownUrisOf("Users@" + domain, 1);
      if (uris == null || uris.length == 0)
        return false;
      for (URI uri : uris) {
        if ("http".equalsIgnoreCase(uri.getScheme())) {
          Result<User> res = new RestUsersClient(uri).getUser(userName, "dummy");
          if (res != null && res.error() == Result.ErrorCode.NOT_FOUND)
            return false;
          return true;
        }
      }
      return true;
    } catch (Exception e) {
      if (e.getMessage() != null && e.getMessage().contains("404"))
        return false;
      return true;
    }
  }

  @Override
  public String postMessage(String pwd, Message msg) {
    if (msg == null || msg.getSender() == null)
      throw new WebApplicationException(Status.FORBIDDEN);
    String s = msg.getSender();
    if (s.contains("<"))
      s = s.substring(s.indexOf("<") + 1, s.indexOf(">"));
    String[] parts = s.split("@");
    String senderDomain = parts.length > 1 ? parts[1] : domain;

    if (!senderDomain.equals(domain)) {
      if (msg.getId() == null)
        throw new WebApplicationException(Status.FORBIDDEN);
      if (hibernate.get(Message.class, msg.getId()) == null)
        hibernate.persist(msg);

      for (String d : msg.getDestination()) {
        if (d.contains("@") && d.split("@")[1].equals(domain) && !checkUserExistsRemotely(d.split("@")[0])) {
          String bounceId = msg.getId() + "." + d;
          if (hibernate.get(Message.class, bounceId) == null) {
            Message err = new Message(bounceId, "System", Collections.singleton(parts[0] + "@" + senderDomain), "Error",
                "User " + d + " does not exist");
            err.setCreationTime(System.currentTimeMillis());
            hibernate.persist(err);
            URI[] uris = discovery.knownUrisOf("Messages@" + senderDomain, 1);
            if (uris != null) {
              for (URI uri : uris) {
                if ("http".equalsIgnoreCase(uri.getScheme())) {
                  client.target(uri).path("/messages").queryParam("pwd", pwd).request()
                      .post(Entity.entity(err, MediaType.APPLICATION_JSON));
                  break;
                }
              }
            }
          }
        }
      }
      return msg.getId();
    }

    User u = validate(parts[0], pwd);
    if (msg.getId() != null && hibernate.get(Message.class, msg.getId()) != null)
      return msg.getId();
    if (msg.getId() == null)
      msg.setId(String.valueOf(Math.abs(new Random().nextLong())));

    msg.setCreationTime(System.currentTimeMillis());
    String senderEmail = u.getName() + "@" + domain;
    msg.setSender(u.getDisplayName() + " <" + senderEmail + ">");

    for (String d : msg.getDestination()) {
      if (d.contains("@") && d.split("@")[1].equals(domain) && !checkUserExistsRemotely(d.split("@")[0])) {
        String bounceId = msg.getId() + "." + d;
        if (hibernate.get(Message.class, bounceId) == null) {
          Message err = new Message(bounceId, "System", Collections.singleton(senderEmail), "Error",
              "User " + d + " does not exist");
          err.setCreationTime(System.currentTimeMillis());
          hibernate.persist(err);
        }
      }
    }
    hibernate.persist(msg);

    Set<String> rem = new HashSet<>();
    for (String d : msg.getDestination())
      if (d.contains("@") && !d.split("@")[1].equals(domain))
        rem.add(d.split("@")[1]);
    for (String rd : rem) {
      URI[] uris = discovery.knownUrisOf("Messages@" + rd, 1);
      if (uris != null) {
        for (URI uri : uris) {
          if ("http".equalsIgnoreCase(uri.getScheme())) {
            client.target(uri).path("/messages").queryParam("pwd", pwd).request()
                .post(Entity.entity(msg, MediaType.APPLICATION_JSON));
            break;
          }
        }
      }
    }
    return msg.getId();
  }

  @Override
  public Message getInboxMessage(String n, String mid, String p) {
    validate(n, p);
    Message m = hibernate.get(Message.class, mid);
    if (m == null)
      throw new WebApplicationException(Status.NOT_FOUND);
    if (m.getDestination() != null && m.getDestination().contains(n + "@" + domain))
      return m;
    throw new WebApplicationException(Status.FORBIDDEN);
  }

  @Override
  public List<Message> getAllInboxMessages(String n, String p) {
    validate(n, p);
    List<Message> res = new ArrayList<>();
    String mail = n + "@" + domain;
    for (Message m : hibernate.jpql("SELECT m FROM Message m", Message.class))
      if (m.getDestination() != null && m.getDestination().contains(mail))
        res.add(m);
    return res;
  }

  @Override
  public List<String> searchInbox(String n, String p, String q) {
    validate(n, p);
    List<String> res = new ArrayList<>();
    String mail = n + "@" + domain;
    for (Message m : hibernate.jpql("SELECT m FROM Message m", Message.class)) {
      if (m.getDestination() != null && m.getDestination().contains(mail)) {
        if (q == null || q.isEmpty()
            || (m.getContents() != null && m.getContents().toLowerCase().contains(q.toLowerCase()))
            || (m.getSubject() != null && m.getSubject().toLowerCase().contains(q.toLowerCase()))) {
          res.add(m.getId());
        }
      }
    }
    return res;
  }

  @Override
  public void removeFromUserInbox(String n, String mid, String p) {
    validate(n, p);
    Message m = hibernate.get(Message.class, mid);
    if (m != null && m.getDestination() != null) {
      Set<String> d = new HashSet<>(m.getDestination());
      if (d.remove(n + "@" + domain)) {
        m.setDestination(d);
        hibernate.update(m);
      }
    }
  }

  @Override
  public void deleteMessage(String n, String mid, String p) {
    Message m = hibernate.get(Message.class, mid);
    if (m == null)
      return;
    String s = m.getSender();
    if (s.contains("<"))
      s = s.substring(s.indexOf("<") + 1, s.indexOf(">"));
    String sDom = s.split("@")[1];
    if (sDom.equals(domain))
      validate(n, p);
    if (!s.equals(n + "@" + sDom))
      throw new WebApplicationException(Status.FORBIDDEN);
    hibernate.delete(m);

    if (sDom.equals(domain)) {
      Set<String> doms = new HashSet<>();
      for (String d : m.getDestination())
        if (!d.endsWith("@" + domain))
          doms.add(d.split("@")[1]);
      for (String rd : doms) {
        URI[] uris = discovery.knownUrisOf("Messages@" + rd, 1);
        if (uris != null) {
          for (URI uri : uris) {
            if ("http".equalsIgnoreCase(uri.getScheme())) {
              client.target(uri).path("/messages/" + n + "/" + mid).queryParam("pwd", p).request().delete();
              break;
            }
          }
        }
      }
    }
  }
}