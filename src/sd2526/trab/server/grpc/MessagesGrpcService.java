package sd2526.trab.server.grpc;

import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import sd2526.trab.api.Message;
import sd2526.trab.api.grpc.GrpcMessagesGrpc;
import sd2526.trab.api.grpc.GrpcUsersGrpc;
import sd2526.trab.api.grpc.Messages.*;
import sd2526.trab.api.grpc.Users.*;
import sd2526.trab.server.persistence.Hibernate;
import sd2526.trab.server.resources.Discovery;
import java.net.URI;
import java.util.*;

public class MessagesGrpcService extends GrpcMessagesGrpc.GrpcMessagesImplBase {
  private final Hibernate hibernate = Hibernate.getInstance();
  private final String domain;
  private final Discovery discovery;
  private final Client jerseyClient;

  public MessagesGrpcService(String domain) throws Exception {
    this.domain = domain;
    this.discovery = new Discovery();
    this.discovery.start();
    this.jerseyClient = ClientBuilder.newClient();
  }

  private record UserInfo(String displayName, String name) {
  }

  private UserInfo validateUser(String name, String pwd) throws Exception {
    URI[] uris = discovery.knownUrisOf("Users@" + domain, 1);
    if (uris == null || uris.length == 0)
      throw new Exception();
    for (URI uri : uris) {
      if ("grpc".equalsIgnoreCase(uri.getScheme())) {
        ManagedChannel ch = ManagedChannelBuilder.forTarget(uri.getHost() + ":" + uri.getPort()).usePlaintext().build();
        try {
          GrpcUser r = GrpcUsersGrpc.newBlockingStub(ch)
              .getUser(GetUserArgs.newBuilder().setName(name).setPwd(pwd).build()).getUser();
          return new UserInfo(r.getDisplayName(), r.getName());
        } finally {
          ch.shutdown();
        }
      }
    }
    for (URI uri : uris) {
      if ("http".equalsIgnoreCase(uri.getScheme())) {
        sd2526.trab.api.java.Result<sd2526.trab.api.User> res = new sd2526.trab.clients.rest.RestUsersClient(uri)
            .getUser(name, pwd);
        if (res == null || !res.isOK())
          throw new Exception();
        return new UserInfo(res.value().getDisplayName(), res.value().getName());
      }
    }
    throw new Exception();
  }

  private boolean checkUserExistsRemotely(String userName) {
    try {
      URI[] uris = discovery.knownUrisOf("Users@" + domain, 1);
      if (uris == null || uris.length == 0)
        return false;
      for (URI uri : uris) {
        if ("grpc".equalsIgnoreCase(uri.getScheme())) {
          ManagedChannel ch = ManagedChannelBuilder.forTarget(uri.getHost() + ":" + uri.getPort()).usePlaintext()
              .build();
          try {
            GrpcUsersGrpc.newBlockingStub(ch)
                .getUser(GetUserArgs.newBuilder().setName(userName).setPwd("dummy").build());
            return true;
          } catch (io.grpc.StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND)
              return false;
            return true;
          } finally {
            ch.shutdown();
          }
        } else if ("http".equalsIgnoreCase(uri.getScheme())) {
          sd2526.trab.api.java.Result<sd2526.trab.api.User> res = new sd2526.trab.clients.rest.RestUsersClient(uri)
              .getUser(userName, "dummy");
          if (res != null && res.error() == sd2526.trab.api.java.Result.ErrorCode.NOT_FOUND)
            return false;
          return true;
        }
      }
      return true;
    } catch (Exception e) {
      if (e.getMessage() != null && e.getMessage().contains("404"))
        return false;
      return true;
    }
  }

  private GrpcMessage toGrpcMessage(Message m) {
    return GrpcMessage.newBuilder().setId(m.getId()).setSender(m.getSender())
        .setSubject(m.getSubject() != null ? m.getSubject() : "")
        .setContents(m.getContents() != null ? m.getContents() : "").setCreationTime(m.getCreationTime())
        .addAllDestination(m.getDestination() != null ? m.getDestination() : new ArrayList<>()).build();
  }

  @Override
  public void postMessage(PostMessageArgs request, StreamObserver<PostMessageResult> responseObserver) {
    try {
      String s = request.getMessage().getSender();
      if (s.contains("<"))
        s = s.substring(s.indexOf("<") + 1, s.indexOf(">"));
      String senderName = s.split("@")[0];
      String senderDom = s.split("@")[1];

      if (!senderDom.equals(domain)) {
        if (request.getMessage().getId() != null && !request.getMessage().getId().isEmpty()) {
          Message msg = new Message(request.getMessage().getId(), request.getMessage().getSender(),
              new HashSet<>(request.getMessage().getDestinationList()), request.getMessage().getSubject(),
              request.getMessage().getContents());
          msg.setCreationTime(request.getMessage().getCreationTime());
          if (hibernate.get(Message.class, msg.getId()) == null)
            hibernate.persist(msg);

          for (String d : msg.getDestination()) {
            if (d.contains("@") && d.split("@")[1].equals(domain) && !checkUserExistsRemotely(d.split("@")[0])) {
              String bounceId = msg.getId() + "." + d;
              if (hibernate.get(Message.class, bounceId) == null) {
                Message err = new Message(bounceId, "System", Collections.singleton(s), "Error",
                    "User " + d + " does not exist");
                err.setCreationTime(System.currentTimeMillis());
                hibernate.persist(err);
                URI[] uris = discovery.knownUrisOf("Messages@" + senderDom, 1);
                if (uris != null) {
                  for (URI uri : uris) {
                    if ("http".equalsIgnoreCase(uri.getScheme())) {
                      jerseyClient.target(uri).path("/messages").queryParam("pwd", request.getPwd()).request()
                          .post(Entity.entity(err, MediaType.APPLICATION_JSON));
                      break;
                    }
                  }
                }
              }
            }
          }
          responseObserver.onNext(PostMessageResult.newBuilder().setMid(msg.getId()).build());
          responseObserver.onCompleted();
          return;
        }
        throw new Exception();
      }

      UserInfo u = validateUser(senderName, request.getPwd());
      Message msg = new Message();
      String mid = request.getMessage().getId();
      if (mid != null && !mid.isEmpty()) {
        if (hibernate.get(Message.class, mid) != null) {
          responseObserver.onNext(PostMessageResult.newBuilder().setMid(mid).build());
          responseObserver.onCompleted();
          return;
        }
        msg.setId(mid);
      } else {
        msg.setId(String.valueOf(Math.abs(new Random().nextLong())));
      }

      String senderEmail = u.name() + "@" + domain;
      msg.setSender(u.displayName() + " <" + senderEmail + ">");
      msg.setSubject(request.getMessage().getSubject());
      msg.setContents(request.getMessage().getContents());
      msg.setDestination(new HashSet<>(request.getMessage().getDestinationList()));
      msg.setCreationTime(System.currentTimeMillis());

      for (String dest : msg.getDestination()) {
        if (dest.contains("@") && dest.split("@")[1].equals(domain) && !checkUserExistsRemotely(dest.split("@")[0])) {
          String bounceId = msg.getId() + "." + dest;
          if (hibernate.get(Message.class, bounceId) == null) {
            Message err = new Message(bounceId, "System", Collections.singleton(senderEmail), "Error",
                "User " + dest + " does not exist");
            err.setCreationTime(System.currentTimeMillis());
            hibernate.persist(err);
          }
        }
      }

      hibernate.persist(msg);

      Set<String> rem = new HashSet<>();
      for (String d : msg.getDestination())
        if (d.contains("@") && !d.split("@")[1].equals(domain))
          rem.add(d.split("@")[1]);
      for (String rd : rem) {
        URI[] uris = discovery.knownUrisOf("Messages@" + rd, 1);
        if (uris != null) {
          for (URI uri : uris) {
            if ("http".equalsIgnoreCase(uri.getScheme())) {
              jerseyClient.target(uri).path("/messages").queryParam("pwd", request.getPwd()).request()
                  .post(Entity.entity(msg, MediaType.APPLICATION_JSON));
              break;
            }
          }
        }
      }

      responseObserver.onNext(PostMessageResult.newBuilder().setMid(msg.getId()).build());
      responseObserver.onCompleted();
    } catch (Exception e) {
      responseObserver.onError(Status.PERMISSION_DENIED.asRuntimeException());
    }
  }

  @Override
  public void getInboxMessage(GetInboxMessageArgs request, StreamObserver<GrpcMessage> responseObserver) {
    try {
      validateUser(request.getName(), request.getPwd());
      Message m = hibernate.get(Message.class, request.getMid());
      if (m == null || !m.getDestination().contains(request.getName() + "@" + domain)) {
        responseObserver.onError(Status.NOT_FOUND.asRuntimeException());
        return;
      }
      responseObserver.onNext(toGrpcMessage(m));
      responseObserver.onCompleted();
    } catch (Exception e) {
      responseObserver.onError(Status.PERMISSION_DENIED.asRuntimeException());
    }
  }

  @Override
  public void getAllInboxMessages(GetAllInboxMessagesArgs request,
      StreamObserver<GetAllInboxMessagesResult> responseObserver) {
    try {
      validateUser(request.getName(), request.getPwd());
      GetAllInboxMessagesResult.Builder builder = GetAllInboxMessagesResult.newBuilder();
      String mail = request.getName() + "@" + domain;
      for (Message m : hibernate.jpql("SELECT m FROM Message m", Message.class))
        if (m.getDestination() != null && m.getDestination().contains(mail))
          builder.addMids(m.getId());
      responseObserver.onNext(builder.build());
      responseObserver.onCompleted();
    } catch (Exception e) {
      responseObserver.onError(Status.PERMISSION_DENIED.asRuntimeException());
    }
  }

  @Override
  public void removeInboxMessage(RemoveInboxMessageArgs request, StreamObserver<Empty> responseObserver) {
    try {
      validateUser(request.getName(), request.getPwd());
      Message m = hibernate.get(Message.class, request.getMid());
      if (m != null && m.getDestination() != null) {
        Set<String> d = new HashSet<>(m.getDestination());
        if (d.remove(request.getName() + "@" + domain)) {
          m.setDestination(d);
          hibernate.update(m);
        }
      }
      responseObserver.onNext(Empty.getDefaultInstance());
      responseObserver.onCompleted();
    } catch (Exception e) {
      responseObserver.onError(Status.PERMISSION_DENIED.asRuntimeException());
    }
  }

  @Override
  public void deleteMessage(DeleteMessageArgs request, StreamObserver<Empty> responseObserver) {
    try {
      Message m = hibernate.get(Message.class, request.getMid());
      if (m == null) {
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
        return;
      }
      String s = m.getSender();
      if (s.contains("<"))
        s = s.substring(s.indexOf("<") + 1, s.indexOf(">"));
      String sDom = s.split("@")[1];
      if (sDom.equals(domain))
        validateUser(request.getName(), request.getPwd());
      if (!s.equals(request.getName() + "@" + sDom)) {
        responseObserver.onError(Status.PERMISSION_DENIED.asRuntimeException());
        return;
      }
      hibernate.delete(m);

      if (sDom.equals(domain)) {
        Set<String> rem = new HashSet<>();
        for (String d : m.getDestination())
          if (!d.endsWith("@" + domain))
            rem.add(d.split("@")[1]);
        for (String rd : rem) {
          URI[] uris = discovery.knownUrisOf("Messages@" + rd, 1);
          if (uris != null) {
            for (URI uri : uris) {
              if ("http".equalsIgnoreCase(uri.getScheme())) {
                jerseyClient.target(uri).path("/messages/" + request.getName() + "/" + request.getMid())
                    .queryParam("pwd", request.getPwd()).request().delete();
                break;
              }
            }
          }
        }
      }

      responseObserver.onNext(Empty.getDefaultInstance());
      responseObserver.onCompleted();
    } catch (Exception e) {
      responseObserver.onError(Status.PERMISSION_DENIED.asRuntimeException());
    }
  }

  @Override
  public void searchInbox(SearchInboxArgs request, StreamObserver<SearchInboxResult> responseObserver) {
    try {
      validateUser(request.getName(), request.getPwd());
      SearchInboxResult.Builder builder = SearchInboxResult.newBuilder();
      String mail = request.getName() + "@" + domain;
      for (Message m : hibernate.jpql("SELECT m FROM Message m", Message.class)) {
        if (m.getDestination() != null && m.getDestination().contains(mail)) {
          String q = request.getQuery().toLowerCase();
          if (q.isEmpty() || (m.getContents() != null && m.getContents().toLowerCase().contains(q))
              || (m.getSubject() != null && m.getSubject().toLowerCase().contains(q)))
            builder.addMids(m.getId());
        }
      }
      responseObserver.onNext(builder.build());
      responseObserver.onCompleted();
    } catch (Exception e) {
      responseObserver.onError(Status.PERMISSION_DENIED.asRuntimeException());
    }
  }
}