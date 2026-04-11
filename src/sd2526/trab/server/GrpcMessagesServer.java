package sd2526.trab.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import sd2526.trab.server.grpc.MessagesGrpcService;
import sd2526.trab.server.resources.Discovery;
import java.net.InetAddress;
import java.util.logging.Logger;

public class GrpcMessagesServer {
  private static final Logger Log = Logger.getLogger(GrpcMessagesServer.class.getName());

  public static void main(String[] args) throws Exception {
    System.setProperty("java.net.preferIPv4Stack", "true");
    String hostname = InetAddress.getLocalHost().getHostName();
    String domain = hostname.contains(".") ? hostname.substring(hostname.indexOf('.') + 1) : "ourorg";
    int port = 8084;

    for (String arg : args) {
      if (arg.matches("\\d+"))
        port = Integer.parseInt(arg);
      else if (!arg.isEmpty())
        domain = arg;
    }

    String ip = InetAddress.getLocalHost().getHostAddress();
    String uri = "grpc://" + ip + ":" + port + "/grpc";
    Log.info("Starting GrpcMessages Server for domain '" + domain + "' @ " + uri);

    Discovery discovery = new Discovery("Messages", uri, domain);
    discovery.start();

    Server server = ServerBuilder.forPort(port).addService(new MessagesGrpcService(domain)).build().start();
    Log.info("GrpcMessages Server '" + domain + "' ready @ " + uri);
    server.awaitTermination();
  }
}