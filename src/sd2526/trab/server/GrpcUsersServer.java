package sd2526.trab.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import sd2526.trab.server.grpc.UsersGrpcService;
import sd2526.trab.server.resources.Discovery;
import java.net.InetAddress;
import java.util.logging.Logger;

public class GrpcUsersServer {
  private static final Logger Log = Logger.getLogger(GrpcUsersServer.class.getName());

  public static void main(String[] args) throws Exception {
    System.setProperty("java.net.preferIPv4Stack", "true");
    String hostname = InetAddress.getLocalHost().getHostName();
    String domain = hostname.contains(".") ? hostname.substring(hostname.indexOf('.') + 1) : "ourorg";
    int port = 8083;

    for (String arg : args) {
      if (arg.matches("\\d+"))
        port = Integer.parseInt(arg);
      else if (!arg.isEmpty())
        domain = arg;
    }

    String ip = InetAddress.getLocalHost().getHostAddress();
    String uri = "grpc://" + ip + ":" + port + "/grpc";
    Log.info("Starting GrpcUsers Server for domain '" + domain + "' @ " + uri);

    Discovery discovery = new Discovery("Users", uri, domain);
    discovery.start();

    UsersGrpcService service;
    try {
      service = UsersGrpcService.class.getConstructor(String.class).newInstance(domain);
    } catch (Exception e) {
      service = UsersGrpcService.class.getConstructor().newInstance();
    }

    Server server = ServerBuilder.forPort(port).addService(service).build().start();
    Log.info("GrpcUsers Server '" + domain + "' ready @ " + uri);
    server.awaitTermination();
  }
}