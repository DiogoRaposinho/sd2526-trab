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
  private final Hibernate hibernate;
  private final Discovery discovery;
  private final Client jerseyClient;

  public MessagesResource(String domain) throws IOException {
    this.domain = domain;
    this.hibernate = Hibernate.getInstance();
    this.discovery = new Discovery();
    this.discovery.start();
    this.jerseyClient = ClientBuilder.newClient();
  }

  private User validateUser(String name, String pwd) {
    URI[] uris = discovery.knownUrisOf("Users@" + domain, 1);
    if (uris == null || uris.length == 0)
      throw new WebApplicationException(Status.SERVICE_UNAVAILABLE);
    Result<User> res = new RestUsersClient(uris[0]).getUser(name, pwd);
    if (res == null || !res.isOK())
      throw new WebApplicationException(Status.FORBIDDEN);
    return res.value();
  }

  private String getDomainFromEmail(String email) {
    String pure = email;
    if (pure.contains("<") && pure.contains(">"))
      pure = pure.substring(pure.indexOf("<") + 1, pure.indexOf(">"));
    return pure.contains("@") ? pure.split("@")[1] : this.domain;
  }

  @Override
  public String postMessage(String pwd, Message msg) {
    if (msg == null || msg.getSender() == null)
      throw new WebApplicationException(Status.FORBIDDEN);
    String sDomain = getDomainFromEmail(msg.getSender());
    if (!sDomain.equals(this.domain)) {
      if (msg.getId() != null) {
        if (hibernate.get(Message.class, msg.getId()) == null)
          hibernate.persist(msg);
        return msg.getId();
      }
      throw new WebApplicationException(Status.FORBIDDEN);
    }
    if (msg.getId() != null && hibernate.get(Message.class, msg.getId()) != null)
      return msg.getId();
    if (msg.getId() == null)
      msg.setId(String.valueOf(Math.abs(new Random().nextLong())));

    String pEmail = msg.getSender();
    if (pEmail.contains("<") && pEmail.contains(">"))
      pEmail = pEmail.substring(pEmail.indexOf("<") + 1, pEmail.indexOf(">"));
    User sUser = validateUser(pEmail.split("@")[0], pwd);

    if (msg.getCreationTime() <= 0)
      msg.setCreationTime(System.currentTimeMillis());
    msg.setSender(sUser.getDisplayName() + " <" + sUser.getName() + "@" + domain + ">");

    Set<String> rDests = new HashSet<>();
    for (String d : msg.getDestination())
      if (d.contains("@") && !d.split("@")[1].equals(this.domain))
        rDests.add(d);

    hibernate.persist(msg);
    if (!rDests.isEmpty())
      forward(msg, rDests, pwd);
    return msg.getId();
  }

  private void forward(Message orig, Set<String> rDests, String pwd) {
    Map<String, Set<String>> byDom = new HashMap<>();
    for (String d : rDests)
      byDom.computeIfAbsent(d.split("@")[1], k -> new HashSet<>()).add(d);
    for (var e : byDom.entrySet()) {
      URI[] uris = discovery.knownUrisOf("Messages@" + e.getKey(), 1);
      if (uris != null && uris.length > 0) {
        Message cp = new Message(orig.getId(), orig.getSender(), e.getValue(), orig.getSubject(), orig.getContents());
        cp.setCreationTime(orig.getCreationTime());
        jerseyClient.target(uris[0]).path("/messages").queryParam("pwd", pwd).request()
            .post(Entity.entity(cp, MediaType.APPLICATION_JSON));
      }
    }
  }

  @Override
  public Message getInboxMessage(String name, String mid, String pwd) {
    validateUser(name, pwd);
    Message m = hibernate.get(Message.class, mid);
    if (m == null)
      throw new WebApplicationException(Status.NOT_FOUND);
    if (m.getDestination() != null && m.getDestination().contains(name + "@" + domain))
      return m;
    throw new WebApplicationException(Status.FORBIDDEN);
  }

  @Override
  public List<Message> getAllInboxMessages(String name, String pwd) {
    validateUser(name, pwd);
    String mail = name + "@" + domain;
    List<Message> all = hibernate.jpql("SELECT m FROM Message m", Message.class);
    List<Message> res = new ArrayList<>();
    for (Message m : all)
      if (m.getDestination() != null && m.getDestination().contains(mail))
        res.add(m);
    return res;
  }

  @Override
  public List<String> searchInbox(String name, String pwd, String q) {
    validateUser(name, pwd);
    String mail = name + "@" + domain;
    List<Message> all = hibernate.jpql("SELECT m FROM Message m", Message.class);
    List<String> res = new ArrayList<>();
    for (Message m : all) {
      if (m.getDestination() != null && m.getDestination().contains(mail)) {
        if (q == null || q.isEmpty()
            || (m.getContents() != null && m.getContents().toLowerCase().contains(q.toLowerCase()))
            || (m.getSubject() != null && m.getSubject().toLowerCase().contains(q.toLowerCase())))
          res.add(m.getId());
      }
    }
    return res;
  }

  @Override
  public void removeFromUserInbox(String name, String mid, String pwd) {
    validateUser(name, pwd);
    Message m = hibernate.get(Message.class, mid);
    if (m != null && m.getDestination() != null) {
      Set<String> ds = new HashSet<>(m.getDestination());
      if (ds.remove(name + "@" + domain)) {
        m.setDestination(ds);
        hibernate.update(m);
      }
    }
  }

  @Override
  public void deleteMessage(String name, String mid, String pwd) {
    Message m = hibernate.get(Message.class, mid);
    if (m == null)
      return;
    String tDom = getDomainFromEmail(m.getSender());
    String mail = name + "@" + tDom;
    if (!m.getSender().contains("<" + mail + ">") && !m.getSender().equals(mail))
      throw new WebApplicationException(Status.FORBIDDEN);
    if (tDom.equals(this.domain))
      validateUser(name, pwd);
    hibernate.delete(m);
    if (tDom.equals(this.domain)) {
      Set<String> rem = new HashSet<>();
      for (String d : m.getDestination())
        if (!d.endsWith("@" + domain))
          rem.add(d);
      for (String d : rem) {
        URI[] uris = discovery.knownUrisOf("Messages@" + d.split("@")[1], 1);
        if (uris != null && uris.length > 0)
          jerseyClient.target(uris[0]).path("/messages/" + name + "/" + mid).queryParam("pwd", pwd).request().delete();
      }
    }
  }
}