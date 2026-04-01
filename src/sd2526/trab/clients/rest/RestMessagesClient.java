package sd2526.trab.clients.rest;

import java.net.URI;
import java.util.List;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.*;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.core.Response.Status;
import sd2526.trab.api.Message;
import sd2526.trab.api.rest.RestMessages;

public class RestMessagesClient implements RestMessages {
  private final WebTarget target;

  public RestMessagesClient(URI serverURI) {
    this.target = ClientBuilder.newClient().target(serverURI).path(RestMessages.PATH);
  }

  private void verifyVoidResponse(Response r) {
    if (r.getStatus() != Status.NO_CONTENT.getStatusCode() && r.getStatus() != Status.OK.getStatusCode())
      throw new WebApplicationException(r.getStatus());
  }

  @Override
  public String postMessage(String pwd, Message msg) {
    Response r = target.queryParam(PWD, pwd).request().post(Entity.entity(msg, MediaType.APPLICATION_JSON));
    if (r.getStatus() == Status.OK.getStatusCode())
      return r.readEntity(String.class);
    throw new WebApplicationException(r.getStatus());
  }

  @Override
  public Message getInboxMessage(String name, String mid, String pwd) {
    // Fixed Path: /messages/mbox/{name}/{mid}
    Response r = target.path(MBOX).path(name).path(mid).queryParam(PWD, pwd).request().get();
    if (r.getStatus() == Status.OK.getStatusCode())
      return r.readEntity(Message.class);
    throw new WebApplicationException(r.getStatus());
  }

  @Override
  public List<Message> getAllInboxMessages(String name, String pwd) {
    // Fixed Path: /messages/mbox/{name}/all
    Response r = target.path(MBOX).path(name).path("all").queryParam(PWD, pwd).request().get();
    if (r.getStatus() == Status.OK.getStatusCode())
      return r.readEntity(new GenericType<List<Message>>() {
      });
    throw new WebApplicationException(r.getStatus());
  }

  @Override
  public List<String> searchInbox(String name, String pwd, String query) {
    // Fixed Path: /messages/mbox/{name}
    Response r = target.path(MBOX).path(name).queryParam(PWD, pwd).queryParam(QUERY, query).request().get();
    if (r.getStatus() == Status.OK.getStatusCode())
      return r.readEntity(new GenericType<List<String>>() {
      });
    throw new WebApplicationException(r.getStatus());
  }

  @Override
  public void removeFromUserInbox(String name, String mid, String pwd) {
    Response r = target.path(MBOX).path(name).path(mid).queryParam(PWD, pwd).request().delete();
    verifyVoidResponse(r);
  }

  @Override
  public void deleteMessage(String name, String mid, String pwd) {
    Response r = target.path(name).path(mid).queryParam(PWD, pwd).request().delete();
    verifyVoidResponse(r);
  }
}