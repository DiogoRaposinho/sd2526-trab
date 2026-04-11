package sd2526.trab.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import sd2526.trab.server.grpc.MessagesGrpcService;
import sd2526.trab.server.resources.Discovery;

import java.net.InetAddress;
import java.util.logging.Logger;

public class GrpcMessagesServer {
  private static final Logger Log = Logger.getLogger(GrpcMessagesServer.class.getName());
  public static final int PORT = 8084;
  public static final String SERVICE = "Messages";

  public static void main(String[] args) {
    try {
      System.setProperty("java.net.preferIPv4Stack", "true");
      String hostname = InetAddress.getLocalHost().getHostName();
      String domain = hostname.contains(".") ? hostname.substring(hostname.indexOf('.') + 1) : "ourorg";
      if (args.length > 0 && args[0] != null && !args[0].isEmpty())
        domain = args[0];

      Server server = ServerBuilder.forPort(PORT)
          .addService(new MessagesGrpcService(domain))
          .build();
      server.start();

      String publicIp = InetAddress.getLocalHost().getHostAddress();
      String publicURI = String.format("grpc://%s:%d", publicIp, PORT);
      Log.info(String.format("%s gRPC Server ready @ %s. URI: %s", SERVICE, domain, publicURI));

      new Discovery(SERVICE, publicURI, domain).start();
      server.awaitTermination();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}