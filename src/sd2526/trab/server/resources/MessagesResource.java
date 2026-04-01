package sd2526.trab.server.resources;

import jakarta.inject.Singleton;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;
import sd2526.trab.api.Message;
import sd2526.trab.api.rest.RestMessages;
import sd2526.trab.clients.rest.RestMessagesClient;
import sd2526.trab.clients.rest.RestUsersClient;
import sd2526.trab.server.persistence.Hibernate;

import java.net.URI;
import java.util.*;
import java.util.logging.Logger;

/**
 * Implementation of the RestMessages interface.
 * Handles local message storage, cross-domain forwarding, and failure
 * notifications.
 */
@Singleton
public class MessagesResource implements RestMessages {

  private static final Logger Log = Logger.getLogger(MessagesResource.class.getName());
  private final String domain;
  private final Hibernate hibernate;
  private final Discovery discovery;

  public MessagesResource(String domain) {
    this.domain = domain;
    this.hibernate = Hibernate.getInstance();
    Discovery tempDiscovery = null;
    try {
      tempDiscovery = new Discovery();
      tempDiscovery.start();
    } catch (Exception e) {
      Log.severe("Failed to start Discovery: " + e.getMessage());
    }
    this.discovery = tempDiscovery;
  }

  /**
   * Validates if the user exists and the password is correct by calling the Users
   * service.
   * Leverages client behavior that throws WebApplicationException on error
   * (403/404).
   */
  private void validateUser(String name, String pwd) {
    URI[] uris = discovery.knownUrisOf("Users@" + domain, 1);
    if (uris == null || uris.length == 0)
      throw new WebApplicationException(Status.SERVICE_UNAVAILABLE);

    // This call validates credentials. Throws 403 or 404 automatically if it fails.
    new RestUsersClient(uris[0]).getUser(name, pwd);
  }

  @Override
  public String postMessage(String pwd, Message msg) {
    Log.info("postMessage: " + msg);
    boolean isFromMyDomain = msg.getSender().endsWith("@" + domain);

    if (isFromMyDomain) {
      // Validate local sender credentials
      validateUser(msg.getSender().split("@")[0], pwd);

      // Assign ID and timestamp if not present
      if (msg.getId() == null)
        msg.setId(String.valueOf(Math.abs(new Random().nextLong())));
      if (msg.getCreationTime() <= 0)
        msg.setCreationTime(System.currentTimeMillis());
    }

    // Persist the message locally
    hibernate.persist(msg);

    if (isFromMyDomain) {
      // Group destinations by domain for forwarding
      Map<String, List<String>> domains = new HashMap<>();
      for (String dest : msg.getDestination()) {
        String d = dest.split("@")[1];
        domains.computeIfAbsent(d, k -> new ArrayList<>()).add(dest);
      }

      domains.forEach((destDomain, dests) -> {
        if (!destDomain.equals(this.domain)) {
          // Forward to remote domain
          Message forwardMsg = new Message(msg.getId(), msg.getSender(), msg.getSubject(), msg.getContents());
          forwardMsg.setDestination(new HashSet<>(dests));
          forwardMsg.setCreationTime(msg.getCreationTime());
          forwardToDomain(destDomain, forwardMsg);
        } else {
          // Check if local destinations exist
          for (String d : dests) {
            if (!checkLocalUser(d.split("@")[0]))
              sendFailureNotification(msg, d, "UNKNOWN USER");
          }
        }
      });
    }
    return msg.getId();
  }

  /**
   * Checks if a local user exists without requiring their password.
   * A 403 error from the Users server indicates the user exists but the password
   * (empty) was wrong.
   */
  private boolean checkLocalUser(String userId) {
    try {
      URI[] uris = discovery.knownUrisOf("Users@" + domain, 1);
      if (uris == null || uris.length == 0)
        return false;

      new RestUsersClient(uris[0]).getUser(userId, "");
      return true;
    } catch (WebApplicationException wae) {
      // 403 means user exists; 404 means they don't
      return wae.getResponse().getStatus() == Status.FORBIDDEN.getStatusCode();
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Attempts to forward a message to a remote domain with a retry loop.
   */
  private void forwardToDomain(String destDomain, Message msg) {
    new Thread(() -> {
      long startTime = System.currentTimeMillis();
      long maxDuration = 90000; // 90 seconds timeout
      boolean sent = false;

      while (System.currentTimeMillis() - startTime < maxDuration) {
        try {
          URI[] uris = discovery.knownUrisOf("Messages@" + destDomain, 1);
          if (uris != null && uris.length > 0) {
            new RestMessagesClient(uris[0]).postMessage("", msg);
            sent = true;
            break;
          }
        } catch (WebApplicationException wae) {
          // If the remote server explicitly rejects the user (404), stop retrying
          if (wae.getResponse().getStatus() == Status.NOT_FOUND.getStatusCode()) {
            for (String user : msg.getDestination()) {
              sendFailureNotification(msg, user, "UNKNOWN USER");
            }
            return;
          }
        } catch (Exception e) {
          Log.info("Attempt to forward to " + destDomain + " failed, retrying...");
        }

        try {
          Thread.sleep(5000); // Wait before next attempt
        } catch (InterruptedException e) {
          break;
        }
      }

      if (!sent) {
        // Notify sender about delivery failure after timeout or unknown domain
        for (String user : msg.getDestination()) {
          sendFailureNotification(msg, user, "UNKNOWN DOMAIN/TIMEOUT");
        }
        Log.severe("FAILED TO FORWARD " + msg.getId() + " TO " + destDomain);
      }
    }).start();
  }

  /**
   * Generates a failure notification message from 'admin' to the original sender.
   */
  private void sendFailureNotification(Message original, String failedUser, String reason) {
    Message errorMsg = new Message();
    errorMsg.setId(original.getId() + "." + failedUser.split("@")[0]);
    errorMsg.setSender("admin@" + domain);
    errorMsg.setDestination(Set.of(original.getSender()));
    errorMsg.setSubject("FAILED TO SEND " + original.getId() + " TO " + failedUser + ": " + reason);
    errorMsg.setContents(original.getContents());
    errorMsg.setCreationTime(System.currentTimeMillis());
    hibernate.persist(errorMsg);
  }

  @Override
  public Message getInboxMessage(String name, String mid, String pwd) {
    validateUser(name, pwd);
    Message m = hibernate.get(Message.class, mid);
    if (m == null)
      throw new WebApplicationException(Status.NOT_FOUND);

    String userEmail = name + "@" + domain;
    // Check if the user is either the sender or a recipient
    if (m.getSender().equals(userEmail) || (m.getDestination() != null && m.getDestination().contains(userEmail))) {
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
  public List<String> searchInbox(String user, String pwd, String query) {
    validateUser(user, pwd);
    List<Message> all = hibernate.getAll(Message.class);
    List<String> res = new ArrayList<>();
    String userEmail = user + "@" + domain;

    for (Message m : all) {
      if (m.getDestination() != null && m.getDestination().contains(userEmail)) {
        // Case-insensitive search in subject and contents
        boolean matches = (query == null || query.isEmpty()) ||
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
    validateUser(name, pwd);
    Message m = hibernate.get(Message.class, mid);
    if (m != null) {
      // Only the sender can delete the message from the system entirely
      if (m.getSender().equals(name + "@" + domain)) {
        hibernate.delete(m);
      } else {
        throw new WebApplicationException(Status.FORBIDDEN);
      }
    }
  }
}