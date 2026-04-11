package sd2526.trab.gateway;

import com.sun.net.httpserver.HttpServer;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import java.net.URI;

public class GatewayServer {

    public static final String BASE_URI = "http://0.0.0.0:8082/rest";

    public static void main(String[] args) {

        ResourceConfig config = new ResourceConfig();
        config.register(sd2526.trab.gateway.resources.GatewayUsersResource.class);
        config.register(sd2526.trab.gateway.resources.GatewayMessagesResource.class);

        HttpServer server = JdkHttpServerFactory.createHttpServer(
                URI.create(BASE_URI),
                config);

        System.out.println("Gateway running at " + BASE_URI);
    }
}