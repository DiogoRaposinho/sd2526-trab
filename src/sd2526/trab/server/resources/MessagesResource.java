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
import java.net.URI;
import java.util.*;
import java.util.logging.Logger;

@Path(RestMessages.PATH) // Mandatory annotation for Jersey mapping
@Singleton // Ensures the resource instance is shared and data persists between requests
public class MessagesResource implements RestMessages {

  private static Logger Log = Logger.getLogger(MessagesResource.class.getName());
  private final String domain;
  private final Hibernate hibernate;

  public MessagesResource(String domain) {
    this.domain = domain;
    this.hibernate = Hibernate.getInstance();
  }

  /**
   * Helper method to validate user credentials using Discovery.
   */
  private void validateUser(String name, String pwd) {
    try {
      // Use Discovery to find the local UsersServer
      Discovery discovery = new Discovery();
      discovery.start();
      URI[] uris = discovery.knownUrisOf("users:" + domain, 1);

      // Use your RestUsersClient to verify the user
      RestUsersClient client = new RestUsersClient(uris[0]);
      Result<User> res = client.getUser(name, pwd);

      if (!res.isOK()) {
        Log.info("User validation failed for: " + name);
        throw new WebApplicationException(Status.FORBIDDEN);
      }
    } catch (Exception e) {
      Log.severe("Error during validation: " + e.getMessage());
      throw new WebApplicationException(Status.FORBIDDEN);
    }
  }

  @Override
  public String postMessage(String pwd, Message msg) {
    Log.info("postMessage: " + msg);

    // Verify that the sender belongs to the server's domain
    if (!msg.getSender().endsWith("@" + domain)) {
      throw new WebApplicationException(Status.FORBIDDEN);
    }

    // Authenticate the sender
    validateUser(msg.getSender().split("@")[0], pwd);

    // Generate a new unique ID and persist using Hibernate
    String mid = String.valueOf(Math.abs(new Random().nextLong()));
    msg.setId(mid);

    hibernate.persist(msg);

    return mid;
  }

  @Override
  public Message getMessage(String name, String mid, String pwd) {
    Log.info("getMessage: " + mid + " for user " + name);
    validateUser(name, pwd);

    // Retrieve the message from the database
    Message m = hibernate.get(Message.class, mid);
    if (m == null)
      throw new WebApplicationException(Status.NOT_FOUND);

    // Check if the user is the sender or a recipient
    if (m.getSender().startsWith(name + "@") || m.getDestination().contains(name + "@" + domain)) {
      return m;
    }

    throw new WebApplicationException(Status.FORBIDDEN);
  }

  @Override
  public List<String> getMessages(String name, String pwd, String query) {
    Log.info("getMessages for " + name + " (query: " + query + ")");
    validateUser(name, pwd);

    // Fetch all messages and filter for the user's inbox
    List<Message> all = hibernate.getAll(Message.class);
    List<String> result = new ArrayList<>();

    for (Message m : all) {
      if (m.getDestination().contains(name + "@" + domain)) {
        boolean matches = (query == null || query.isEmpty()) ||
            (m.getContents().toLowerCase().contains(query.toLowerCase()) ||
                m.getSubject().toLowerCase().contains(query.toLowerCase()));
        if (matches)
          result.add(m.getId());
      }
    }
    return result;
  }

  @Override
  public void removeFromUserInbox(String name, String mid, String pwd) {
    Log.info("removeFromUserInbox: " + mid + " for user " + name);
    validateUser(name, pwd);

    Message m = hibernate.get(Message.class, mid);
    if (m != null) {
      // Remove the user from the destination set and update the DB
      m.getDestination().remove(name + "@" + domain);
      hibernate.update(m);
    }
  }

  @Override
  public void deleteMessage(String name, String mid, String pwd) {
    Log.info("deleteMessage: " + mid + " by user " + name);
    validateUser(name, pwd);

    Message m = hibernate.get(Message.class, mid);
    // Only the sender is allowed to delete the message globally
    if (m != null && m.getSender().startsWith(name + "@")) {
      hibernate.delete(m);
    } else if (m != null) {
      throw new WebApplicationException(Status.FORBIDDEN);
    }
  }
}