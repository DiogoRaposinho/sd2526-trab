package sd2526.trab.server;

import java.net.InetAddress;
import java.net.URI;
import java.util.logging.Logger;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import sd2526.trab.server.resources.Discovery;
import sd2526.trab.server.resources.MessagesResource;

public class MessagesServer {

  private static Logger Log = Logger.getLogger(MessagesServer.class.getName());

  static {
    // Prefer IPv4 to avoid multicast issues with some network stacks
    System.setProperty("java.net.preferIPv4Stack", "true");

    // Simplified logging format for better readability in the console
    System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
  }

  // Use a different port from UsersServer (8080) to allow running both locally
  public static final int PORT = 8081;

  // Service name used for Discovery by the other servers
  public static final String SERVICE = "messages";

  // URI format string for the server endpoint
  private static final String SERVER_URI_FMT = "http://%s:%d/rest";

  public static void main(String[] args) {

    try {
      // Check if the domain argument was provided via command line
      if (args.length == 0) {
        Log.info("Domain argument missing.");
        return;
      }

      String domain = args[0];

      // Configure Jersey resources
      ResourceConfig config = new ResourceConfig();
      config.register(new MessagesResource(domain));

      // Force the server to listen on all interfaces (0.0.0.0)
      // This allows connection via localhost and the network IP simultaneously
      String ip = "0.0.0.0";
      String serverURI = String.format(SERVER_URI_FMT, ip, PORT);

      // Instantiate the JDK HTTP Server with the specified configuration
      JdkHttpServerFactory.createHttpServer(URI.create(serverURI), config);

      // Get the actual network IP for Discovery and Logs
      String publicIp = InetAddress.getLocalHost().getHostAddress();
      String publicURI = String.format(SERVER_URI_FMT, publicIp, PORT);

      Log.info(String.format("%s Server ('%s') ready @ %s. Local access: http://localhost:%d/rest\n",
          SERVICE, domain, publicURI, PORT));

      // Start service discovery announcements so other servers can find this one
      // The service name is specialized with the domain (e.g., messages:fct)
      String discoveryServiceName = SERVICE + ":" + domain;
      new Discovery(discoveryServiceName, publicURI).start();

    } catch (Exception e) {
      Log.severe(e.getMessage());
      e.printStackTrace();
    }
  }
}