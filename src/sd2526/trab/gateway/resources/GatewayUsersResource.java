package sd2526.trab.gateway.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.net.http.*;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
public class GatewayUsersResource {

    private final HttpClient client = HttpClient.newHttpClient();
    private final String USERS_URL = "http://users:8080/rest/users";

    // ----------- HELPER -----------

    private String extractName(String userId) {
        return userId.contains("@") ? userId.split("@")[0] : userId;
    }

    private Response forward(HttpRequest request) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            return Response.status(response.statusCode())
                    .entity(response.body())
                    .build();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private HttpRequest.Builder baseRequest(String url) {
        return HttpRequest.newBuilder(URI.create(url));
    }

    // ----------- CREATE USER -----------

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createUser(String body) {

        HttpRequest request = baseRequest(USERS_URL)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .build();

        return forward(request);
    }

    // ----------- GET USER -----------

    @GET
    @Path("{name}")
    public Response getUser(@PathParam("name") String name,
            @QueryParam("pwd") String pwd) {

        String realName = extractName(name);

        String url = USERS_URL + "/" + realName + "?pwd=" + pwd;

        HttpRequest request = baseRequest(url).GET().build();

        return forward(request);
    }

    // ----------- UPDATE USER -----------

    @PUT
    @Path("{name}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateUser(@PathParam("name") String name,
            @QueryParam("pwd") String pwd,
            String body) {

        String realName = extractName(name);

        String url = USERS_URL + "/" + realName + "?pwd=" + pwd;

        HttpRequest request = baseRequest(url)
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .build();

        return forward(request);
    }

    // ----------- DELETE USER -----------

    @DELETE
    @Path("{name}")
    public Response deleteUser(@PathParam("name") String name,
            @QueryParam("pwd") String pwd) {

        String realName = extractName(name);

        String url = USERS_URL + "/" + realName + "?pwd=" + pwd;

        HttpRequest request = baseRequest(url).DELETE().build();

        return forward(request);
    }

    // ----------- SEARCH USERS -----------

    @GET
    public Response searchUsers(@QueryParam("name") String name,
            @QueryParam("pwd") String pwd,
            @QueryParam("query") String query) {

        String url = USERS_URL
                + "?name=" + name
                + "&pwd=" + pwd
                + (query != null ? "&query=" + query : "");

        HttpRequest request = baseRequest(url).GET().build();

        return forward(request);
    }
}