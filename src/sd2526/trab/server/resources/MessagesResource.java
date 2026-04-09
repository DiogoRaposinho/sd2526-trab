package sd2526.trab.server.resources;

import jakarta.inject.Singleton;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
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

/**
 * Implementation of the Messages Service Resource.
 * Handles message persistence, retrieval, and inter-service communication.
 */
@Singleton
public class MessagesResource implements RestMessages {

  private static final Logger Log = Logger.getLogger(MessagesResource.class.getName());

  private final String domain;
  private final Hibernate hibernate;
  private final Discovery discovery;

  public MessagesResource(String domain) throws IOException {
    this.domain = domain;
    this.hibernate = Hibernate.getInstance();
    this.discovery = new Discovery();

    discovery.start();
  }

  private void validateUser(String name, String pwd) {
    try {
      URI[] uris = discovery.knownUrisOf("Users@" + domain, 1);

      if (uris == null || uris.length == 0) {
        Log.severe("Users service not found in Discovery");
        throw new WebApplicationException(Status.SERVICE_UNAVAILABLE);
      }

      URI userServerURI = uris[0];

      RestUsersClient client = new RestUsersClient(userServerURI);
      Result<User> res = client.getUser(name, pwd);

      if (res == null || !res.isOK()) {
        throw new WebApplicationException(Status.FORBIDDEN);
      }

    } catch (WebApplicationException e) {
      throw e;
    } catch (Exception e) {
      Log.severe("User validation failed: " + e.getMessage());
      throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
    }
  }

  @Override
  public String postMessage(String pwd, Message msg) {
    Log.info("postMessage: " + msg);

    if (msg == null || msg.getSender() == null || !msg.getSender().endsWith("@" + domain)) {
      throw new WebApplicationException(Status.FORBIDDEN);
    }

    validateUser(msg.getSender().split("@")[0], pwd);

    String mid = String.valueOf(Math.abs(new Random().nextLong()));
    msg.setId(mid);

    if (msg.getCreationTime() <= 0) {
      msg.setCreationTime(System.currentTimeMillis());
    }

    // 🔥 FIX HERE
    User senderUser = hibernate.get(User.class, msg.getSender().split("@")[0]);

    String formattedSender = senderUser.getDisplayName() +
        " <" + senderUser.getName() + "@" + senderUser.getDomain() + ">";

    msg.setSender(formattedSender);

    hibernate.persist(msg);
    return mid;
  }

  @Override
  public Message getInboxMessage(String name, String mid, String pwd) {
    validateUser(name, pwd);

    Message m = hibernate.get(Message.class, mid);
    if (m == null) {
      throw new WebApplicationException(Status.NOT_FOUND);
    }

    String userEmail = name + "@" + domain;

    // ONLY inbox access allowed
    if (m.getDestination() != null && m.getDestination().contains(userEmail)) {
      return m;
    }

    throw new WebApplicationException(Status.FORBIDDEN);
  }

  @Override
  public List<Message> getAllInboxMessages(String name, String pwd) {
    validateUser(name, pwd);
    List<Message> all = hibernate.getAll(Message.class);
    List<Message> userInbox = new ArrayList<>();
    String userEmail = name + "@" + domain;

    for (Message m : all) {
      if (m.getDestination() != null && m.getDestination().contains(userEmail)) {
        userInbox.add(m);
      }
    }
    return userInbox;
  }

  @Override
  public List<String> searchInbox(String name, String pwd, String query) {
    Log.info("searchInbox for " + name + " (query: " + query + ")");

    validateUser(name, pwd);

    List<Message> all = hibernate.getAll(Message.class);
    List<String> res = new ArrayList<>();
    String userEmail = name + "@" + domain;

    for (Message m : all) {
      if (m.getDestination() != null && m.getDestination().contains(userEmail)) {
        boolean matches = query == null || query.isEmpty() ||
            (m.getContents() != null && m.getContents().toLowerCase().contains(query.toLowerCase())) ||
            (m.getSubject() != null && m.getSubject().toLowerCase().contains(query.toLowerCase()));

        if (matches)
          res.add(m.getId());
      }
    }
    return res;
  }

  @Override
  public void removeFromUserInbox(String name, String mid, String pwd) {
    Log.info("removeFromUserInbox: " + mid + " for user " + name);
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
    Log.info("deleteMessage: " + mid + " by user " + name);
    validateUser(name, pwd);

    Message m = hibernate.get(Message.class, mid);
    if (m != null) {
      if (m.getSender().equals(name + "@" + domain)) {
        hibernate.delete(m);
      } else {
        throw new WebApplicationException(Status.FORBIDDEN);
      }
    }
  }
}