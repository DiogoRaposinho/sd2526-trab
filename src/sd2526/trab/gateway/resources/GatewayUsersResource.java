package sd2526.trab.gateway.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import sd2526.trab.server.resources.Discovery;

import java.net.URI;
import java.net.http.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
public class GatewayUsersResource {

    private final HttpClient client = HttpClient.newHttpClient();
    private final Discovery discovery;

    public GatewayUsersResource() {
        try {
            this.discovery = new Discovery();
            this.discovery.start();
        } catch (Exception e) {
            throw new RuntimeException("Falha ao inicializar o Discovery no Gateway Users", e);
        }
    }

    private String getBaseUrl(String domain) {
        try {
            URI[] uris = discovery.knownUrisOf("Users@" + domain, 1);
            if (uris != null && uris.length > 0) {
                return uris[0].toString() + "/users";
            }
        } catch (Exception e) {

        }

        return "http://users0." + domain + ":8080/rest/users";
    }

    private String extractName(String userId) {
        return userId != null && userId.contains("@") ? userId.split("@")[0] : userId;
    }

    private String extractDomain(String userId, String fallback) {
        return userId != null && userId.contains("@") ? userId.split("@")[1] : fallback;
    }

    private Response forward(HttpRequest request) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return Response.status(response.statusCode())
                    .entity(response.body())
                    .build();
        } catch (Exception e) {
            throw new WebApplicationException(Response.Status.SERVICE_UNAVAILABLE);
        }
    }

    private HttpRequest.Builder baseRequest(String url) {
        return HttpRequest.newBuilder(URI.create(url));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createUser(String body) {
        String domain = "ourorg";

        Matcher m = Pattern.compile("\"domain\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        if (m.find())
            domain = m.group(1);

        String url = getBaseUrl(domain);
        HttpRequest request = baseRequest(url)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .build();
        return forward(request);
    }

    @GET
    @Path("{name}")
    public Response getUser(@PathParam("name") String name, @QueryParam("pwd") String pwd) {
        String domain = extractDomain(name, "ourorg");
        String realName = extractName(name);
        String url = getBaseUrl(domain) + "/" + realName + "?pwd=" + pwd;
        return forward(baseRequest(url).GET().build());
    }

    @PUT
    @Path("{name}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateUser(@PathParam("name") String name, @QueryParam("pwd") String pwd, String body) {
        String domain = extractDomain(name, "ourorg");
        String realName = extractName(name);
        String url = getBaseUrl(domain) + "/" + realName + "?pwd=" + pwd;
        HttpRequest request = baseRequest(url)
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .build();
        return forward(request);
    }

    @DELETE
    @Path("{name}")
    public Response deleteUser(@PathParam("name") String name, @QueryParam("pwd") String pwd) {
        String domain = extractDomain(name, "ourorg");
        String realName = extractName(name);
        String url = getBaseUrl(domain) + "/" + realName + "?pwd=" + pwd;
        return forward(baseRequest(url).DELETE().build());
    }

    @GET
    public Response searchUsers(@QueryParam("name") String name, @QueryParam("pwd") String pwd,
            @QueryParam("query") String query) {
        String domain = extractDomain(name, "ourorg");
        String url = getBaseUrl(domain) + "?name=" + name + "&pwd=" + pwd + (query != null ? "&query=" + query : "");
        return forward(baseRequest(url).GET().build());
    }
}