package sd2526.trab.clients.rest;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import sd2526.trab.api.Message;

import java.net.URI;
import java.util.logging.Logger;

public class RestMessagesClient {

  private static final Logger Log = Logger.getLogger(RestMessagesClient.class.getName());

  private static final int MAX_RETRIES = 3;
  private static final long RETRY_SLEEP = 1000; // ms

  private final URI serverURI;
  private final Client client;

  public RestMessagesClient(URI serverURI) {
    this.serverURI = serverURI;
    this.client = ClientBuilder.newClient();
  }

  public void postMessage(String pwd, Message msg) {
    for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
      try {
        Response r = client
            .target(serverURI)
            .path("/messages") // Junta-se ao /rest do serverURI
            .queryParam("pwd", pwd)
            .request(MediaType.APPLICATION_JSON)
            .post(Entity.entity(msg, MediaType.APPLICATION_JSON));

        if (r.getStatus() == Response.Status.OK.getStatusCode()) {
          return; // Sucesso!
        }

        Log.warning("postMessage falhou na tentativa " + (attempt + 1) + " com status HTTP: " + r.getStatus());

      } catch (Exception e) {
        Log.warning("Erro de rede no postMessage: " + e.getMessage());
      }

      sleep();
    }
  }

  public void deleteMessage(String user, String mid, String pwd) {
    for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
      try {
        Response r = client
            .target(serverURI)
            .path("/messages/" + user + "/" + mid)
            .queryParam("pwd", pwd)
            .request()
            .delete();

        if (r.getStatus() == Response.Status.OK.getStatusCode()
            || r.getStatus() == Response.Status.NO_CONTENT.getStatusCode()) {
          return; // Apagado com sucesso!
        }

        Log.warning("deleteMessage falhou na tentativa " + (attempt + 1) + " com status HTTP: " + r.getStatus());

      } catch (Exception e) {
        Log.warning("Erro de rede no deleteMessage: " + e.getMessage());
      }

      sleep();
    }
  }

  private void sleep() {
    try {
      Thread.sleep(RETRY_SLEEP);
    } catch (InterruptedException ignored) {
    }
  }
}