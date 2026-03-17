package sd2526.trab.server.resources;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.hibernate.exception.ConstraintViolationException;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;
import sd2526.trab.api.User;
import sd2526.trab.api.rest.RestUsers;

import jakarta.inject.Singleton;
import sd2526.trab.server.persistence.Hibernate;

@Path(RestUsers.PATH)
@Singleton
public class UsersResource implements RestUsers {

  private final String domain;

  private final Hibernate hibernate;
  private static Logger Log = Logger.getLogger(UsersResource.class.getName());

  public UsersResource(String domain) {
    hibernate = Hibernate.getInstance();
    this.domain = domain;
  }

  /**
   * // Auxiliary method that validates and records access
   * private User getAndValidateUser(String name, String pwd) {
   * var user = users.get(name);
   * if (user == null || !user.getPwd().equals(pwd)) {
   * Log.info("User does not exist or password is incorrect.");
   * throw new WebApplicationException(Status.FORBIDDEN);
   * }
   * return user;
   * }
   **/

  @Override
  public String postUser(User user) {
    Log.info("postUser : " + user);

    // Check if user data is valid
    if (user.getName() == null || user.getPwd() == null || user.getDisplayName() == null
        || user.getDomain() == null) {
      Log.info("User object invalid.");
      throw new WebApplicationException(Status.BAD_REQUEST);
    }

    try {
      hibernate.persist(user);
    } catch (ConstraintViolationException e) {
      e.printStackTrace(); // This exception is due to the user already existing...
      Log.info("User already exists.");
      throw new WebApplicationException(Status.CONFLICT);
    } catch (Exception x) {
      x.printStackTrace(); // Un-expected exception. Signal internal server error.
      throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
    }

    return user.getName() + "@" + user.getDomain();
  }

  @Override
  public User getUser(String name, String pwd) {
    Log.info("getUser : name = " + name + "; pwd = " + pwd);

    // Check if parameters are valid
    if (name == null || pwd == null) {
      Log.info("Name or password null.");
      throw new WebApplicationException(Status.BAD_REQUEST);
    }

    User user = null;
    try {
      user = hibernate.get(User.class, name);
    } catch (Exception x) {
      x.printStackTrace(); // Un-expected exception. Signal internal server error.
      throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
    }

    // Check if user exists and password matches
    if (user == null || !user.getPwd().equals(pwd)) {
      Log.info("User does not exist or password is incorrect.");
      throw new WebApplicationException(Status.FORBIDDEN);
    }

    return user;
  }

  @Override
  public User updateUser(String name, String pwd, User info) {
    Log.info("updateUser : name = " + name + "; pwd = " + pwd + " ; info = " + info);

    /**
     * Ver se tenho de retirar esta parte, pq já uso o método getUser(name, pwd)
     * nesta classe
     */
    if (name == null || pwd == null || info == null) {
      Log.info("Name or password doesn't exist");
      throw new WebApplicationException(Status.BAD_REQUEST);
    }

    User user = this.getUser(name, pwd);

    try {
      String newPwd = info.getPwd();
      String newDisplayName = info.getDisplayName();

      if (newPwd != null)
        user.setPwd(newPwd);

      if (newDisplayName != null)
        user.setDisplayName(newDisplayName);

      hibernate.update(user);

    } catch (Exception x) {
      x.printStackTrace(); // Un-expected exception. Signal internal server error.
      throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
    }

    return user;
  }

  public User deleteUser(String name, String pwd) {
    Log.info("deleteUser : name = " + name + "; pwd = " + pwd);

    /**
     * Ver se tenho de retirar esta parte, pq já uso o método getUser(name, pwd)
     * nesta classe
     */
    if (name == null || pwd == null) {
      Log.info("Name or password null.");
      throw new WebApplicationException(Status.BAD_REQUEST);
    }

    User user = this.getUser(name, pwd);

    try {
      hibernate.delete(user);

      return user;

    } catch (Exception x) {
      x.printStackTrace();
      throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
    }

  }

  public List<User> searchUsers(String name, String pwd, String pattern) {
    Log.info("searchUsers : name = " + name + "; pwd = " + pwd + "; pattern = " + pattern);
    getUser(name, pwd);

    try {
      String query = String.format("SELECT u FROM User u WHERE LOWER(u.name) LIKE '%%%s%%'", pattern.toLowerCase());
      List<User> results = hibernate.jpql(query, User.class);

      results.forEach(u -> u.setPwd(""));
      return results;

    } catch (Exception x) {
      x.printStackTrace();
      throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
    }
  }
}