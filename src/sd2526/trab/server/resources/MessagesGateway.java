package sd2526.trab.server.resources;

import java.net.URI;
import java.util.List;
import jakarta.inject.Singleton;
import jakarta.ws.rs.*;
import sd2526.trab.api.Message;
import sd2526.trab.api.rest.RestMessages;
import sd2526.trab.clients.rest.RestMessagesClient;

@Singleton
@Path(RestMessages.PATH)
public class MessagesGateway implements RestMessages {
  private final String domain;
  private final Discovery discovery;

  public MessagesGateway(String domain, Discovery discovery) {
    this.domain = domain;
    this.discovery = discovery;
  }

  private URI getURI() {
    URI[] uris = discovery.knownUrisOf("Messages@" + domain, 1);
    if (uris.length == 0)
      throw new WebApplicationException(503);
    return uris[0];
  }

  @Override
  public String postMessage(String p, Message m) {
    return new RestMessagesClient(getURI()).postMessage(p, m);
  }

  @Override
  public Message getInboxMessage(String n, String m, String p) {
    return new RestMessagesClient(getURI()).getInboxMessage(n, m, p);
  }

  @Override
  public List<Message> getAllInboxMessages(String n, String p) {
    return new RestMessagesClient(getURI()).getAllInboxMessages(n, p);
  }

  @Override
  public List<String> searchInbox(String n, String p, String q) {
    return new RestMessagesClient(getURI()).searchInbox(n, p, q);
  }

  @Override
  public void removeFromUserInbox(String n, String m, String p) {
    new RestMessagesClient(getURI()).removeFromUserInbox(n, m, p);
  }

  @Override
  public void deleteMessage(String n, String m, String p) {
    new RestMessagesClient(getURI()).deleteMessage(n, m, p);
  }
}