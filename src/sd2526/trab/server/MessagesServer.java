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
    System.setProperty("java.net.preferIPv4Stack", "true");

    System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
  }

  public static final int PORT = 8081;
  public static final String SERVICE = "Messages";
  private static final String SERVER_URI_FMT = "http://%s:%d/rest";

  public static void main(String[] args) {

    try {
      String hostname = InetAddress.getLocalHost().getHostName();
      String domain = hostname.contains(".") ? hostname.substring(hostname.indexOf('.') + 1) : "ourorg";

      if (args.length > 0 && args[0] != null && !args[0].isEmpty()) {
        domain = args[0];
      }

      ResourceConfig config = new ResourceConfig();
      config.register(new MessagesResource(domain));

      String ip = "0.0.0.0";
      String serverURI = String.format(SERVER_URI_FMT, ip, PORT);

      JdkHttpServerFactory.createHttpServer(URI.create(serverURI), config);

      String publicIp = InetAddress.getLocalHost().getHostAddress();
      String publicURI = String.format(SERVER_URI_FMT, publicIp, PORT);

      Log.info(String.format("%s Server ('%s') ready @ %s. Local access: http://localhost:%d/rest\n",
          SERVICE, domain, publicURI, PORT));

      new Discovery(SERVICE, publicURI, domain).start();

    } catch (Exception e) {
      Log.severe(e.getMessage());
      e.printStackTrace();
    }
  }
}