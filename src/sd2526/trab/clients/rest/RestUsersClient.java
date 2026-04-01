package sd2526.trab.clients.rest;

import java.net.URI;
import java.util.List;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.*;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.core.Response.Status;
import sd2526.trab.api.User;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.rest.RestUsers;

public class RestUsersClient implements RestUsers {
  private final WebTarget target;

  public RestUsersClient(URI serverURI) {
    this.target = ClientBuilder.newClient().target(serverURI).path(RestUsers.PATH);
  }

  private <T> Result<T> verifyResponse(Response r, Class<T> clazz) {
    if (r.getStatus() == Status.OK.getStatusCode())
      return Result.ok(r.readEntity(clazz));
    // Propagate HTTP error status
    throw new WebApplicationException(r.getStatus());
  }

  @Override
  public String postUser(User user) {
    Response r = target.request().post(Entity.entity(user, MediaType.APPLICATION_JSON));
    return verifyResponse(r, String.class).value();
  }

  @Override
  public User getUser(String name, String pwd) {
    Response r = target.path(name).queryParam(PWD, pwd).request().get();
    return verifyResponse(r, User.class).value();
  }

  @Override
  public User updateUser(String name, String pwd, User info) {
    Response r = target.path(name).queryParam(PWD, pwd).request().put(Entity.entity(info, MediaType.APPLICATION_JSON));
    return verifyResponse(r, User.class).value();
  }

  @Override
  public User deleteUser(String name, String pwd) {
    Response r = target.path(name).queryParam(PWD, pwd).request().delete();
    return verifyResponse(r, User.class).value();
  }

  @Override
  public List<User> searchUsers(String name, String pwd, String pattern) {
    Response r = target.queryParam(QUERY, pattern).queryParam(NAME, name).queryParam(PWD, pwd).request().get();
    if (r.getStatus() == Status.OK.getStatusCode())
      return r.readEntity(new GenericType<List<User>>() {
      });
    throw new WebApplicationException(r.getStatus());
  }
}