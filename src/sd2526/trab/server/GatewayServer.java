package sd2526.trab.server;

import java.net.InetAddress;
import java.net.URI;
import java.util.logging.Logger;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import sd2526.trab.server.resources.Discovery;
import sd2526.trab.server.resources.UsersGateway;
import sd2526.trab.server.resources.MessagesGateway;

/**
 * Gateway Server that acts as a proxy for Users and Messages services.
 */
public class GatewayServer {
  private static Logger Log = Logger.getLogger(GatewayServer.class.getName());

  static {
    // Simplified logging format for console output
    System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
  }

  public static void main(String[] args) {
    try {
      // Ensure the domain argument is provided
      if (args.length < 1) {
        System.err.println("Usage: java sd2526.trab.server.GatewayServer <domain>");
        return;
      }

      String domain = args[0];

      // Start discovery to locate backend services (Users and Messages)
      Discovery discovery = new Discovery();
      discovery.start();

      // Configure Jersey resources for the Gateway
      ResourceConfig config = new ResourceConfig();
      config.register(new UsersGateway(domain, discovery));
      config.register(new MessagesGateway(domain, discovery));

      // Listen on all network interfaces (0.0.0.0) - essential for Docker
      // connectivity
      String bindIp = "0.0.0.0";
      String serverURI = String.format("http://%s:8082/rest", bindIp);

      // Launch the JDK HTTP Server
      JdkHttpServerFactory.createHttpServer(URI.create(serverURI), config);

      // Get the actual network IP of the container for Discovery announcement
      String publicIp = InetAddress.getLocalHost().getHostAddress();
      String publicURI = String.format("http://%s:8082/rest", publicIp);

      Log.info(String.format("Gateway Server ('%s') ready at %s", domain, publicURI));

      // Announce the Gateway service in the multicast network
      new Discovery("Gateway", publicURI, domain).start();

    } catch (Exception e) {
      Log.severe("Fatal error starting Gateway Server: " + e.getMessage());
      e.printStackTrace();
    }
  }
}