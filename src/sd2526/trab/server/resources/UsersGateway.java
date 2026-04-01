package sd2526.trab.server.resources;

import java.net.URI;
import java.util.List;
import jakarta.inject.Singleton;
import jakarta.ws.rs.*;
import sd2526.trab.api.User;
import sd2526.trab.api.rest.RestUsers;
import sd2526.trab.clients.rest.RestUsersClient;

@Singleton
@Path(RestUsers.PATH)
public class UsersGateway implements RestUsers {
  private final String domain;
  private final Discovery discovery;

  public UsersGateway(String domain, Discovery discovery) {
    this.domain = domain;
    this.discovery = discovery;
  }

  private URI getURI() {
    URI[] uris = discovery.knownUrisOf("Users@" + domain, 1);
    if (uris.length == 0)
      throw new WebApplicationException(503);
    return uris[0];
  }

  @Override
  public String postUser(User u) {
    return new RestUsersClient(getURI()).postUser(u);
  }

  @Override
  public User getUser(String n, String p) {
    return new RestUsersClient(getURI()).getUser(n, p);
  }

  @Override
  public User updateUser(String n, String p, User i) {
    return new RestUsersClient(getURI()).updateUser(n, p, i);
  }

  @Override
  public User deleteUser(String n, String p) {
    return new RestUsersClient(getURI()).deleteUser(n, p);
  }

  @Override
  public List<User> searchUsers(String n, String p, String q) {
    return new RestUsersClient(getURI()).searchUsers(n, p, q);
  }
}