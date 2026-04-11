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
import java.util.logging.Logger;

@Singleton
@Path("/messages")
public class MessagesResource implements RestMessages {

  private static final Logger Log = Logger.getLogger(MessagesResource.class.getName());

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

  private boolean localUserExists(String destName, String senderName, String senderPwd, RestUsersClient usersClient) {
    try {
      Result<List<User>> res = usersClient.searchUsers(senderName, senderPwd, destName);
      if (res != null && res.isOK() && res.value() != null) {
        for (User u : res.value()) {
          if (u.getName().equalsIgnoreCase(destName))
            return true;
        }
      }
      return false;
    } catch (Exception e) {
      return false;
    }
  }

  private String getDomainFromEmail(String email) {
    String pureEmail = email;
    if (pureEmail.contains("<") && pureEmail.contains(">")) {
      pureEmail = pureEmail.substring(pureEmail.indexOf("<") + 1, pureEmail.indexOf(">"));
    }
    return pureEmail.contains("@") ? pureEmail.split("@")[1] : this.domain;
  }

  @Override
  public String postMessage(String pwd, Message msg) {
    Log.info("postMessage: " + msg);

    if (msg == null || msg.getSender() == null) {
      throw new WebApplicationException(Status.FORBIDDEN);
    }

    String senderDomain = getDomainFromEmail(msg.getSender());

    if (!senderDomain.equals(this.domain)) {
      if (msg.getId() != null) {
        Message existing = hibernate.get(Message.class, msg.getId());
        if (existing == null)
          hibernate.persist(msg);
        return msg.getId();
      } else {
        throw new WebApplicationException(Status.FORBIDDEN);
      }
    }

    if (msg.getId() != null) {
      Message existing = hibernate.get(Message.class, msg.getId());
      if (existing != null)
        return existing.getId();
    } else {
      msg.setId(String.valueOf(Math.abs(new Random().nextLong())));
    }

    URI[] userUris = discovery.knownUrisOf("Users@" + domain, 1);
    if (userUris == null || userUris.length == 0)
      throw new WebApplicationException(Status.SERVICE_UNAVAILABLE);

    RestUsersClient usersClient = new RestUsersClient(userUris[0]);
    String pureEmail = msg.getSender();
    if (pureEmail.contains("<") && pureEmail.contains(">")) {
      pureEmail = pureEmail.substring(pureEmail.indexOf("<") + 1, pureEmail.indexOf(">"));
    }
    String senderName = pureEmail.split("@")[0];

    Result<User> senderRes = usersClient.getUser(senderName, pwd);
    if (senderRes == null || !senderRes.isOK())
      throw new WebApplicationException(Status.FORBIDDEN);

    User senderUser = senderRes.value();

    if (msg.getCreationTime() <= 0)
      msg.setCreationTime(System.currentTimeMillis());

    String senderEmail = senderUser.getName() + "@" + domain;
    msg.setSender(senderUser.getDisplayName() + " <" + senderEmail + ">");

    Set<String> localDests = new HashSet<>();
    Set<String> remoteDests = new HashSet<>();

    for (String dest : msg.getDestination()) {
      if (!dest.contains("@"))
        continue;
      if (dest.split("@")[1].equals(this.domain))
        localDests.add(dest);
      else
        remoteDests.add(dest);
    }

    for (String dest : localDests) {
      String destName = dest.split("@")[0];
      if (!localUserExists(destName, senderName, pwd, usersClient)) {
        String errorId = msg.getId() + "." + dest;
        Message errorMsg = new Message(errorId, "System", senderEmail, "Failure Notification",
            "User " + dest + " does not exist.");
        errorMsg.setDestination(Set.of(senderEmail));
        hibernate.persist(errorMsg);
      }
    }

    hibernate.persist(msg);

    if (!remoteDests.isEmpty()) {
      forwardToRemoteDomains(msg, remoteDests, pwd);
    }

    return msg.getId();
  }

  private void forwardToRemoteDomains(Message original, Set<String> remoteDests, String pwd) {
    Map<String, Set<String>> byDomain = new HashMap<>();
    for (String dest : remoteDests)
      byDomain.computeIfAbsent(dest.split("@")[1], k -> new HashSet<>()).add(dest);

    for (Map.Entry<String, Set<String>> entry : byDomain.entrySet()) {
      String remoteDomain = entry.getKey();
      try {
        URI[] uris = discovery.knownUrisOf("Messages@" + remoteDomain, 1);
        if (uris == null || uris.length == 0)
          continue;

        Message copy = new Message(original.getId(), original.getSender(), entry.getValue(), original.getSubject(),
            original.getContents());
        copy.setCreationTime(original.getCreationTime());

        jerseyClient.target(uris[0])
            .path("/messages")
            .queryParam("pwd", pwd)
            .request()
            .post(Entity.entity(copy, MediaType.APPLICATION_JSON));
      } catch (Exception e) {
        Log.warning("Erro ao enviar para " + remoteDomain + ": " + e.getMessage());
      }
    }
  }

  @Override
  public Message getInboxMessage(String name, String mid, String pwd) {
    validateUser(name, pwd);
    Message m = hibernate.get(Message.class, mid);
    if (m == null)
      throw new WebApplicationException(Status.NOT_FOUND);

    String userEmail = name + "@" + domain;
    if (m.getDestination() != null && m.getDestination().contains(userEmail)) {
      return m;
    }
    throw new WebApplicationException(Status.FORBIDDEN);
  }

  @Override
  public List<Message> getAllInboxMessages(String name, String pwd) {
    validateUser(name, pwd);
    String userEmail = name + "@" + domain;

    String query = "SELECT m FROM Message m WHERE '" + userEmail + "' member of m.destination";
    List<Message> userInbox = hibernate.jpql(query, Message.class);

    return userInbox != null ? userInbox : new ArrayList<>();
  }

  @Override
  public List<String> searchInbox(String name, String pwd, String queryStr) {
    validateUser(name, pwd);
    String userEmail = name + "@" + domain;

    String query = "SELECT m FROM Message m WHERE '" + userEmail + "' member of m.destination";
    List<Message> inbox = hibernate.jpql(query, Message.class);

    List<String> res = new ArrayList<>();
    for (Message m : inbox) {
      boolean matches = queryStr == null || queryStr.isEmpty()
          || (m.getContents() != null && m.getContents().toLowerCase().contains(queryStr.toLowerCase()))
          || (m.getSubject() != null && m.getSubject().toLowerCase().contains(queryStr.toLowerCase()));
      if (matches)
        res.add(m.getId());
    }
    return res;
  }

  @Override
  public void removeFromUserInbox(String name, String mid, String pwd) {
    validateUser(name, pwd);
    Message m = hibernate.get(Message.class, mid);
    if (m != null && m.getDestination() != null) {
      if (m.getDestination().remove(name + "@" + domain)) {
        hibernate.update(m);
      }
    }
  }

  @Override
  public void deleteMessage(String name, String mid, String pwd) {
    Message m = hibernate.get(Message.class, mid);
    if (m == null)
      return;

    String trueSenderDomain = getDomainFromEmail(m.getSender());

    if (trueSenderDomain.equals(this.domain)) {
      validateUser(name, pwd);
      String userEmail = name + "@" + this.domain;
      if (!m.getSender().contains("<" + userEmail + ">") && !m.getSender().equals(userEmail)) {
        throw new WebApplicationException(Status.FORBIDDEN);
      }
    } else {
      String expectedEmailPrefix = name + "@" + trueSenderDomain;
      if (!m.getSender().contains("<" + expectedEmailPrefix + ">") && !m.getSender().equals(expectedEmailPrefix)) {
        throw new WebApplicationException(Status.FORBIDDEN);
      }
    }

    hibernate.delete(m);

    if (trueSenderDomain.equals(this.domain)) {
      Set<String> remoteDests = new HashSet<>();
      for (String dest : m.getDestination()) {
        if (!dest.endsWith("@" + domain)) {
          remoteDests.add(dest);
        }
      }
      deleteFromRemoteDomains(name, mid, pwd, remoteDests);
    }
  }

  private void deleteFromRemoteDomains(String name, String mid, String pwd, Set<String> remoteDests) {
    Set<String> domainsToContact = new HashSet<>();
    for (String dest : remoteDests) {
      domainsToContact.add(dest.split("@")[1]);
    }

    for (String remoteDomain : domainsToContact) {
      try {
        URI[] uris = discovery.knownUrisOf("Messages@" + remoteDomain, 1);
        if (uris != null && uris.length > 0) {
          jerseyClient.target(uris[0])
              .path("/messages/" + name + "/" + mid)
              .queryParam("pwd", pwd)
              .request()
              .delete();
        }
      } catch (Exception e) {
        Log.warning("Erro ao enviar delete para " + remoteDomain + ": " + e.getMessage());
      }
    }
  }
}