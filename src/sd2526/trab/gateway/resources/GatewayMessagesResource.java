package sd2526.trab.gateway.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import sd2526.trab.server.resources.Discovery;

import java.net.URI;
import java.net.http.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Path("/messages")
@Produces(MediaType.APPLICATION_JSON)
public class GatewayMessagesResource {

    private final HttpClient client = HttpClient.newHttpClient();
    private final Discovery discovery;

    public GatewayMessagesResource() {
        try {
            this.discovery = new Discovery();
            this.discovery.start();
        } catch (Exception e) {
            throw new RuntimeException("Falha ao inicializar o Discovery no Gateway Messages", e);
        }
    }

    // Método inteligente para descobrir onde está o serviço de Messages
    private String getBaseUrl(String domain) {
        try {
            URI[] uris = discovery.knownUrisOf("Messages@" + domain, 1);
            if (uris != null && uris.length > 0) {
                return uris[0].toString() + "/messages";
            }
        } catch (Exception e) {
            // Ignora e usa fallback
        }
        // Fallback seguro
        return "http://messages0." + domain + ":8081/rest/messages";
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
    public Response sendMessage(@QueryParam("pwd") String pwd, String body) {
        String domain = "ourorg";
        // Tenta extrair o domínio de destino a partir do sender no corpo JSON
        Matcher m = Pattern.compile("\"sender\"\\s*:\\s*\"[^\"]*?@([^\"]+)\"").matcher(body);
        if (m.find())
            domain = m.group(1);

        String url = getBaseUrl(domain) + "?pwd=" + pwd;
        HttpRequest request = baseRequest(url)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .build();
        return forward(request);
    }

    @GET
    @Path("mbox/{user}")
    public Response inbox(@PathParam("user") String user, @QueryParam("pwd") String pwd,
            @QueryParam("query") String query) {
        String domain = extractDomain(user, "ourorg");
        String url = getBaseUrl(domain) + "/mbox/" + user + "?pwd=" + pwd + (query != null ? "&query=" + query : "");
        return forward(baseRequest(url).GET().build());
    }

    @GET
    @Path("mbox/{user}/all")
    public Response inboxAll(@PathParam("user") String user, @QueryParam("pwd") String pwd) {
        String domain = extractDomain(user, "ourorg");
        String url = getBaseUrl(domain) + "/mbox/" + user + "/all?pwd=" + pwd;
        return forward(baseRequest(url).GET().build());
    }

    @GET
    @Path("mbox/{user}/{mid}")
    public Response getMessage(@PathParam("user") String user, @PathParam("mid") String mid,
            @QueryParam("pwd") String pwd) {
        String domain = extractDomain(user, "ourorg");
        String url = getBaseUrl(domain) + "/mbox/" + user + "/" + mid + "?pwd=" + pwd;
        return forward(baseRequest(url).GET().build());
    }

    @DELETE
    @Path("mbox/{user}/{mid}")
    public Response deleteMessage(@PathParam("user") String user, @PathParam("mid") String mid,
            @QueryParam("pwd") String pwd) {
        String domain = extractDomain(user, "ourorg");
        String url = getBaseUrl(domain) + "/mbox/" + user + "/" + mid + "?pwd=" + pwd;
        return forward(baseRequest(url).DELETE().build());
    }

    @DELETE
    @Path("{user}/{mid}")
    public Response deleteMessageCompletely(@PathParam("user") String user, @PathParam("mid") String mid,
            @QueryParam("pwd") String pwd) {
        String domain = extractDomain(user, "ourorg");
        String url = getBaseUrl(domain) + "/" + user + "/" + mid + "?pwd=" + pwd;
        return forward(baseRequest(url).DELETE().build());
    }
}