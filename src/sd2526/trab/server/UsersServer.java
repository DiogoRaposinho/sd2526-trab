package sd2526.trab.server;

import java.net.InetAddress;
import java.net.URI;
import java.util.logging.Logger;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import jakarta.inject.Singleton;
import sd2526.trab.server.resources.Discovery;
import sd2526.trab.server.resources.UsersResource;

@Singleton
public class UsersServer {

    private static Logger Log = Logger.getLogger(UsersServer.class.getName());

    static {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
    }

    public static final int PORT = 8080;
    public static final String SERVICE = "Users";
    private static final String SERVER_URI_FMT = "http://%s:%s/rest";

    public static void main(String[] args) {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            String domain = hostname.contains(".") ? hostname.substring(hostname.indexOf('.') + 1) : "ourorg";

            if (args.length > 0 && args[0] != null && !args[0].isEmpty()) {
                domain = args[0];
            }

            ResourceConfig config = new ResourceConfig();

            UsersResource usersResource = new UsersResource(domain);
            config.register(usersResource);

            String ip = "0.0.0.0";
            String serverURI = String.format(SERVER_URI_FMT, ip, PORT);

            JdkHttpServerFactory.createHttpServer(URI.create(serverURI), config);

            String publicIp = InetAddress.getLocalHost().getHostAddress();
            String publicURI = String.format(SERVER_URI_FMT, publicIp, PORT);

            Log.info(String.format("%s Server ready @ %s. Local access: http://localhost:%d/rest\n",
                    SERVICE, domain, PORT));

            new Discovery(SERVICE, publicURI, domain).start();

        } catch (Exception e) {
            Log.severe(e.getMessage());
            e.printStackTrace();
        }
    }
}