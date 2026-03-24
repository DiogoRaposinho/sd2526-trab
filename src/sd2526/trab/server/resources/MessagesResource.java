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
  private final Discovery discovery; // Store discovery instance to avoid re-starting threads

  // CONSTRUCTOR: Must be public and handled via instance registration in
  // Server.java
  public MessagesResource(String domain) {
    this.domain = domain;
    this.hibernate = Hibernate.getInstance();

    // Initialize Discovery safely inside a try-catch to prevent Server crash
    Discovery tempDiscovery = null;
    try {
      tempDiscovery = new Discovery();
      tempDiscovery.start();
      Log.info("Discovery started successfully.");
    } catch (Exception e) {
      Log.severe("Could not start Discovery: " + e.getMessage());
      // Fallback will be handled during validation
    }
    this.discovery = tempDiscovery;
  }

  /**
   * Helper method to validate user credentials using Discovery.
   */
  private void validateUser(String name, String pwd) {
    try {
      URI userServerURI = null;

      // 1. Try to find the Users server via Multicast Discovery
      if (discovery != null) {
        URI[] uris = discovery.knownUrisOf("users:" + domain, 1);
        if (uris != null && uris.length > 0) {
          userServerURI = uris[0];
        }
      }

      // 2. DOCKER FALLBACK: If Discovery fails (common in Windows Docker),
      // use the container name which is resolved by Docker's internal DNS.
      if (userServerURI == null) {
        Log.warning("Discovery failed. Using Docker network fallback: http://users-fct:8080/rest");
        userServerURI = URI.create("http://users-fct:8080/rest");
      }

      // 3. Perform the REST call to the Users Server
      RestUsersClient client = new RestUsersClient(userServerURI);
      Result<User> res = client.getUser(name, pwd);

      // 4. Validate result
      if (res == null || !res.isOK()) {
        Log.info("User validation failed for: " + name);
        throw new WebApplicationException(Status.FORBIDDEN);
      }
    } catch (WebApplicationException wae) {
      throw wae; // Propagate JAX-RS exceptions
    } catch (Exception e) {
      Log.severe("Error during validation: " + e.getMessage());
      // Map any other error to Forbidden to avoid leaking server details
      throw new WebApplicationException(Status.FORBIDDEN);
    }
  }

  @Override
  public String postMessage(String pwd, Message msg) {
    Log.info("postMessage: " + msg);

    // Verify that the sender belongs to the server's domain
    if (msg == null || msg.getSender() == null || !msg.getSender().endsWith("@" + domain)) {
      throw new WebApplicationException(Status.FORBIDDEN);
    }

    // Authenticate the sender by calling the Users Server
    validateUser(msg.getSender().split("@")[0], pwd);

    // Generate a new unique ID and persist using Hibernate
    String mid = String.valueOf(Math.abs(new Random().nextLong()));
    msg.setId(mid);

    // Ensure creation time is set if not provided by client
    if (msg.getCreationTime() <= 0) {
      msg.setCreationTime(System.currentTimeMillis());
    }

    hibernate.persist(msg);
    return mid;
  }

  @Override
  public Message getMessage(String name, String mid, String pwd) {
    Log.info("getMessage: " + mid + " for user " + name);
    validateUser(name, pwd);

    Message m = hibernate.get(Message.class, mid);
    if (m == null)
      throw new WebApplicationException(Status.NOT_FOUND);

    // Check authorization: user must be sender or recipient
    if (m.getSender().equals(name + "@" + domain)
        || (m.getDestination() != null && m.getDestination().contains(name + "@" + domain))) {
      return m;
    }

    throw new WebApplicationException(Status.FORBIDDEN);
  }

  @Override
  public List<String> getMessages(String name, String pwd, String query) {
    Log.info("getMessages for " + name + " (query: " + query + ")");
    validateUser(name, pwd);

    List<Message> all = hibernate.getAll(Message.class);
    List<String> result = new ArrayList<>();

    for (Message m : all) {
      if (m.getDestination() != null && m.getDestination().contains(name + "@" + domain)) {
        boolean matches = (query == null || query.isEmpty()) ||
            (m.getContents() != null && m.getContents().toLowerCase().contains(query.toLowerCase()) ||
                (m.getSubject() != null && m.getSubject().toLowerCase().contains(query.toLowerCase())));
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
    // Only the sender is allowed to delete the message globally
    if (m != null && m.getSender().equals(name + "@" + domain)) {
      hibernate.delete(m);
    } else if (m != null) {
      throw new WebApplicationException(Status.FORBIDDEN);
    }
  }
}