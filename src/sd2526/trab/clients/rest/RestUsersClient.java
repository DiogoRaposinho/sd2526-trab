package sd2526.trab.clients.rest;

import java.net.URI;
import java.util.List;
import java.util.logging.Logger;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import sd2526.trab.api.User;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.java.Result.ErrorCode;
import sd2526.trab.api.java.Users;
import sd2526.trab.api.rest.RestUsers;

public class RestUsersClient implements Users {
  private static Logger Log = Logger.getLogger(RestUsersClient.class.getName());

  protected static final int READ_TIMEOUT = 5000;
  protected static final int CONNECT_TIMEOUT = 5000;

  protected static final int MAX_RETRIES = 3;
  protected static final int RETRY_SLEEP = 5000;

  final URI serverURI;
  final Client client;
  final WebTarget target;

  public RestUsersClient(URI serverURI) {
    this.serverURI = serverURI;
    ClientConfig config = new ClientConfig();

    config.property(ClientProperties.READ_TIMEOUT, READ_TIMEOUT);
    config.property(ClientProperties.CONNECT_TIMEOUT, CONNECT_TIMEOUT);

    this.client = ClientBuilder.newClient(config);
    this.target = client.target(serverURI).path(RestUsers.PATH);
  }

  @Override
  public Result<String> postUser(User user) {
    for (int i = 0; i < MAX_RETRIES; i++) {
      try {
        Response r = target.request()
            .accept(MediaType.APPLICATION_JSON)
            .post(Entity.entity(user, MediaType.APPLICATION_JSON));

        return verifyResponse(r, String.class);
      } catch (ProcessingException x) {
        handleProcessingException(x);
      }
    }
    return Result.error(ErrorCode.TIMEOUT);
  }

  @Override
  public Result<User> getUser(String name, String pwd) {
    for (int i = 0; i < MAX_RETRIES; i++) {
      try {
        Response r = target.path(name)
            .queryParam(RestUsers.PWD, pwd)
            .request()
            .accept(MediaType.APPLICATION_JSON)
            .get();

        return verifyResponse(r, User.class);
      } catch (ProcessingException x) {
        handleProcessingException(x);
      }
    }
    return Result.error(ErrorCode.TIMEOUT);
  }

  @Override
  public Result<User> updateUser(String name, String pwd, User user) {
    for (int i = 0; i < MAX_RETRIES; i++) {
      try {
        Response r = target.path(name)
            .queryParam(RestUsers.PWD, pwd)
            .request()
            .accept(MediaType.APPLICATION_JSON)
            .put(Entity.entity(user, MediaType.APPLICATION_JSON));

        return verifyResponse(r, User.class);
      } catch (ProcessingException x) {
        handleProcessingException(x);
      }
    }
    return Result.error(ErrorCode.TIMEOUT);
  }

  @Override
  public Result<User> deleteUser(String name, String pwd) {
    for (int i = 0; i < MAX_RETRIES; i++) {
      try {
        Response r = target.path(name)
            .queryParam(RestUsers.PWD, pwd)
            .request()
            .accept(MediaType.APPLICATION_JSON)
            .delete();

        return verifyResponse(r, User.class);
      } catch (ProcessingException x) {
        handleProcessingException(x);
      }
    }
    return Result.error(ErrorCode.TIMEOUT);
  }

  @Override
  public Result<List<User>> searchUsers(String pattern, String name, String pwd) {
    for (int i = 0; i < MAX_RETRIES; i++) {
      try {
        Response r = target.queryParam(RestUsers.QUERY, pattern)
            .queryParam(RestUsers.NAME, name)
            .queryParam(RestUsers.PWD, pwd)
            .request()
            .accept(MediaType.APPLICATION_JSON)
            .get();

        if (r.getStatus() != Status.OK.getStatusCode())
          return Result.error(getErrorCodeFrom(r.getStatus()));

        return Result.ok(r.readEntity(new GenericType<List<User>>() {
        }));
      } catch (ProcessingException x) {
        handleProcessingException(x);
      }
    }
    return Result.error(ErrorCode.TIMEOUT);
  }

  private void handleProcessingException(ProcessingException x) {
    Log.info(x.getMessage());
    try {
      Thread.sleep(RETRY_SLEEP);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private <T> Result<T> verifyResponse(Response r, Class<T> clazz) {
    int status = r.getStatus();
    if (status != Status.OK.getStatusCode()) {
      return Result.error(getErrorCodeFrom(status));
    }
    return Result.ok(r.readEntity(clazz));
  }

  public static ErrorCode getErrorCodeFrom(int status) {
    return switch (status) {
      case 200 -> ErrorCode.OK;
      case 409 -> ErrorCode.CONFLICT;
      case 403 -> ErrorCode.FORBIDDEN;
      case 404 -> ErrorCode.NOT_FOUND;
      case 400 -> ErrorCode.BAD_REQUEST;
      case 500 -> ErrorCode.INTERNAL_ERROR;
      case 501 -> ErrorCode.NOT_IMPLEMENTED;
      default -> ErrorCode.INTERNAL_ERROR;
    };
  }
}