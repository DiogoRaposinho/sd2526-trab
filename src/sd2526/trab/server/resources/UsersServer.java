package sd2526.trab.server.resources;

import java.net.InetAddress;
import java.net.URI;
import java.util.logging.Logger;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import jakarta.inject.Singleton;

@Singleton
public class UsersServer {

  private static Logger Log = Logger.getLogger(UsersServer.class.getName());

  static {
    System.setProperty("java.net.preferIPv4Stack", "true");
    System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
  }

  public static final int PORT = 8080;
  public static final String SERVICE = "users";
  private static final String SERVER_URI_FMT = "http://%s:%s/rest";

  public static void main(String[] args) {
    try {

      if (args.length == 0) {
        Log.info("Domain argument missing.");
        return;
      }

      String domain = args[0];

      ResourceConfig config = new ResourceConfig();

      // Create the resource instance with the domain
      UsersResource usersResource = new UsersResource(domain);

      // Register the instance directly so Jersey doesn't try to create it using an
      // empty constructor
      config.register(usersResource);

      // Force the server to listen on all interfaces (0.0.0.0)
      // This allows connection via localhost and the network IP simultaneously
      String ip = "0.0.0.0";
      String serverURI = String.format(SERVER_URI_FMT, ip, PORT);

      JdkHttpServerFactory.createHttpServer(URI.create(serverURI), config);

      // Get the actual network IP for Discovery and Logs
      String publicIp = InetAddress.getLocalHost().getHostAddress();
      String publicURI = String.format(SERVER_URI_FMT, publicIp, PORT);

      Log.info(String.format("%s Server ready @ %s. Local access: http://localhost:%d/rest\n", SERVICE, domain, PORT));

      // Start the service announcement for automatic discovery
      new Discovery("Users", publicURI, domain).start();

    } catch (Exception e) {
      Log.severe(e.getMessage());
      e.printStackTrace();
    }
  }
}