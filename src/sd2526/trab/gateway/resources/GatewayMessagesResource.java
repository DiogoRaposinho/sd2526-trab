package sd2526.trab.gateway.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.net.http.*;

@Path("/messages")
@Produces(MediaType.APPLICATION_JSON)
public class GatewayMessagesResource {

    private final HttpClient client = HttpClient.newHttpClient();
    private final String BASE_URL = "http://messages:8081/rest/messages";

    // ----------- HELPER -----------

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

    // ----------- POST MESSAGE -----------

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response sendMessage(@QueryParam("pwd") String pwd, String body) {

        String url = BASE_URL + "?pwd=" + pwd;

        HttpRequest request = baseRequest(url)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .build();

        return forward(request);
    }

    // ----------- SEARCH INBOX -----------

    @GET
    @Path("mbox/{user}")
    public Response inbox(@PathParam("user") String user,
            @QueryParam("pwd") String pwd,
            @QueryParam("query") String query) {

        String url = BASE_URL + "/mbox/" + user
                + "?pwd=" + pwd
                + (query != null ? "&query=" + query : "");

        HttpRequest request = baseRequest(url).GET().build();

        return forward(request);
    }

    // ----------- ALL INBOX -----------

    @GET
    @Path("mbox/{user}/all")
    public Response inboxAll(@PathParam("user") String user,
            @QueryParam("pwd") String pwd) {

        String url = BASE_URL + "/mbox/" + user + "/all?pwd=" + pwd;

        HttpRequest request = baseRequest(url).GET().build();

        return forward(request);
    }

    // ----------- GET SINGLE MESSAGE -----------

    @GET
    @Path("mbox/{user}/{mid}")
    public Response getMessage(@PathParam("user") String user,
            @PathParam("mid") String mid,
            @QueryParam("pwd") String pwd) {

        String url = BASE_URL + "/mbox/" + user + "/" + mid + "?pwd=" + pwd;

        HttpRequest request = baseRequest(url).GET().build();

        return forward(request);
    }

    // ----------- REMOVE FROM INBOX -----------

    @DELETE
    @Path("mbox/{user}/{mid}")
    public Response deleteMessage(@PathParam("user") String user,
            @PathParam("mid") String mid,
            @QueryParam("pwd") String pwd) {

        String url = BASE_URL + "/mbox/" + user + "/" + mid + "?pwd=" + pwd;

        HttpRequest request = baseRequest(url).DELETE().build();

        return forward(request);
    }

    // ----------- DELETE MESSAGE COMPLETELY -----------

    @DELETE
    @Path("{user}/{mid}")
    public Response deleteMessageCompletely(@PathParam("user") String user,
            @PathParam("mid") String mid,
            @QueryParam("pwd") String pwd) {

        String url = BASE_URL + "/" + user + "/" + mid + "?pwd=" + pwd;

        HttpRequest request = baseRequest(url).DELETE().build();

        return forward(request);
    }
}