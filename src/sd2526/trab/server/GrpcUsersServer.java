package sd2526.trab.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import sd2526.trab.server.grpc.UsersGrpcService;
import sd2526.trab.server.resources.Discovery;

import java.net.InetAddress;
import java.util.logging.Logger;

public class GrpcUsersServer {
  private static final Logger Log = Logger.getLogger(GrpcUsersServer.class.getName());
  public static final String SERVICE = "Users";

  public static void main(String[] args) {
    try {
      System.setProperty("java.net.preferIPv4Stack", "true");

      String hostname = InetAddress.getLocalHost().getHostName();
      String domain = hostname.contains(".") ? hostname.substring(hostname.indexOf('.') + 1) : "ourorg";

      int port = 8083;
      if (args.length > 0) {
        try {
          port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
          domain = args[0];
        }
      }
      if (args.length > 1) {
        try {
          port = Integer.parseInt(args[1]);
        } catch (NumberFormatException ignored) {
        }
      }

      Server server = ServerBuilder.forPort(port)
          .addService(new UsersGrpcService(domain))
          .build();
      server.start();

      String publicIp = InetAddress.getLocalHost().getHostAddress();
      String publicURI = String.format("grpc://%s:%d/grpc", publicIp, port);
      Log.info(String.format("%s gRPC Server ready @ %s. URI: %s", SERVICE, domain, publicURI));

      new Discovery(SERVICE, publicURI, domain).start();

      server.awaitTermination();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}