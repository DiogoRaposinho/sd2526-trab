package sd2526.trab.server.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import sd2526.trab.api.Message;
import sd2526.trab.api.User;
import sd2526.trab.api.grpc.GrpcMessagesGrpc;
import sd2526.trab.api.grpc.Messages.*;
import sd2526.trab.server.persistence.Hibernate;
import com.google.protobuf.Empty;
import java.util.*;

public class MessagesGrpcService extends GrpcMessagesGrpc.GrpcMessagesImplBase {
  private final Hibernate hibernate = Hibernate.getInstance();
  private final String domain;

  public MessagesGrpcService(String domain) {
    this.domain = domain;
  }

  private GrpcMessage toGrpcMessage(Message m) {
    return GrpcMessage.newBuilder()
        .setId(m.getId())
        .setSender(m.getSender())
        .setSubject(m.getSubject() != null ? m.getSubject() : "")
        .setContents(m.getContents() != null ? m.getContents() : "")
        .setCreationTime(m.getCreationTime())
        .addAllDestination(m.getDestination() != null ? m.getDestination() : new ArrayList<>())
        .build();
  }

  @Override
  public void postMessage(PostMessageArgs request, StreamObserver<PostMessageResult> responseObserver) {
    try {
      Message msg = new Message();

      String senderId = request.getMessage().getSender();
      String pureEmail = senderId;
      if (pureEmail.contains("<") && pureEmail.contains(">")) {
        pureEmail = pureEmail.substring(pureEmail.indexOf("<") + 1, pureEmail.indexOf(">"));
      }

      String senderName = pureEmail.split("@")[0];
      String senderDomain = pureEmail.split("@").length > 1 ? pureEmail.split("@")[1] : domain;

      if (senderDomain.equals(this.domain)) {
        User senderUser = hibernate.get(User.class, senderName);
        if (senderUser == null || !senderUser.getPwd().equals(request.getPwd())) {
          responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Forbidden").asRuntimeException());
          return;
        }
        String dispName = senderUser.getDisplayName() != null ? senderUser.getDisplayName() : senderName;
        msg.setSender(dispName + " <" + senderName + "@" + domain + ">");
      } else {
        msg.setSender(request.getMessage().getSender());
      }

      msg.setSubject(request.getMessage().getSubject());
      msg.setContents(request.getMessage().getContents());
      msg.setDestination(new HashSet<>(request.getMessage().getDestinationList()));

      if (request.getMessage().getId() == null || request.getMessage().getId().isEmpty()) {
        msg.setId(String.valueOf(Math.abs(new Random().nextLong())));
      } else {
        msg.setId(request.getMessage().getId());
      }
      msg.setCreationTime(System.currentTimeMillis());

      hibernate.persist(msg);

      responseObserver.onNext(PostMessageResult.newBuilder().setMid(msg.getId()).build());
      responseObserver.onCompleted();
    } catch (Exception e) {
      responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void getInboxMessage(GetInboxMessageArgs request, StreamObserver<GrpcMessage> responseObserver) {
    try {
      User user = hibernate.get(User.class, request.getName());
      if (user == null || !user.getPwd().equals(request.getPwd())) {
        responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Forbidden").asRuntimeException());
        return;
      }

      Message m = hibernate.get(Message.class, request.getMid());
      if (m == null) {
        responseObserver.onError(Status.NOT_FOUND.withDescription("Message not found").asRuntimeException());
        return;
      }

      String userEmail = request.getName() + "@" + domain;
      if (m.getDestination() == null || !m.getDestination().contains(userEmail)) {
        responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Forbidden").asRuntimeException());
        return;
      }

      responseObserver.onNext(toGrpcMessage(m));
      responseObserver.onCompleted();
    } catch (Exception e) {
      responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void getAllInboxMessages(GetAllInboxMessagesArgs request,
      StreamObserver<GetAllInboxMessagesResult> responseObserver) {
    try {
      User user = hibernate.get(User.class, request.getName());
      if (user == null || !user.getPwd().equals(request.getPwd())) {
        responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Forbidden").asRuntimeException());
        return;
      }

      String userEmail = request.getName() + "@" + domain;
      List<Message> allMessages = hibernate.jpql("SELECT m FROM Message m", Message.class);

      GetAllInboxMessagesResult.Builder builder = GetAllInboxMessagesResult.newBuilder();
      for (Message m : allMessages) {
        if (m.getDestination() != null && m.getDestination().contains(userEmail)) {
          builder.addMids(m.getId());
        }
      }
      responseObserver.onNext(builder.build());
      responseObserver.onCompleted();
    } catch (Exception e) {
      responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void removeInboxMessage(RemoveInboxMessageArgs request, StreamObserver<Empty> responseObserver) {
    try {
      User user = hibernate.get(User.class, request.getName());
      if (user == null || !user.getPwd().equals(request.getPwd())) {
        responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Forbidden").asRuntimeException());
        return;
      }

      Message m = hibernate.get(Message.class, request.getMid());
      if (m != null && m.getDestination() != null) {
        Set<String> newDests = new HashSet<>(m.getDestination());
        if (newDests.remove(request.getName() + "@" + domain)) {
          m.setDestination(newDests);
          hibernate.update(m);
        }
      }
      responseObserver.onNext(Empty.getDefaultInstance());
      responseObserver.onCompleted();
    } catch (Exception e) {
      responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void deleteMessage(DeleteMessageArgs request, StreamObserver<Empty> responseObserver) {
    try {
      User user = hibernate.get(User.class, request.getName());
      if (user == null || !user.getPwd().equals(request.getPwd())) {
        responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Forbidden").asRuntimeException());
        return;
      }

      Message m = hibernate.get(Message.class, request.getMid());
      if (m != null) {
        String expectedPrefix = request.getName() + "@" + domain;
        if (m.getSender().contains("<" + expectedPrefix + ">") || m.getSender().equals(expectedPrefix)) {
          hibernate.delete(m);
        } else {
          responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Forbidden").asRuntimeException());
          return;
        }
      }
      responseObserver.onNext(Empty.getDefaultInstance());
      responseObserver.onCompleted();
    } catch (Exception e) {
      responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void searchInbox(SearchInboxArgs request, StreamObserver<SearchInboxResult> responseObserver) {
    try {
      User user = hibernate.get(User.class, request.getName());
      if (user == null || !user.getPwd().equals(request.getPwd())) {
        responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Forbidden").asRuntimeException());
        return;
      }

      String userEmail = request.getName() + "@" + domain;
      List<Message> allMessages = hibernate.jpql("SELECT m FROM Message m", Message.class);

      SearchInboxResult.Builder builder = SearchInboxResult.newBuilder();
      for (Message m : allMessages) {
        if (m.getDestination() != null && m.getDestination().contains(userEmail)) {
          boolean matches = request.getQuery().isEmpty()
              || (m.getContents() != null && m.getContents().toLowerCase().contains(request.getQuery().toLowerCase()))
              || (m.getSubject() != null && m.getSubject().toLowerCase().contains(request.getQuery().toLowerCase()));
          if (matches)
            builder.addMids(m.getId());
        }
      }
      responseObserver.onNext(builder.build());
      responseObserver.onCompleted();
    } catch (Exception e) {
      responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }
  }
}