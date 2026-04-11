package sd2526.trab.server.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import sd2526.trab.api.Message;
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
      msg.setSender(request.getMessage().getSender());
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
      Message m = hibernate.get(Message.class, request.getMid());
      if (m == null) {
        responseObserver.onError(Status.NOT_FOUND.withDescription("Message not found").asRuntimeException());
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
      String userEmail = request.getName() + "@" + domain;
      String query = "SELECT m FROM Message m WHERE '" + userEmail + "' member of m.destination";
      List<Message> userInbox = hibernate.jpql(query, Message.class);

      GetAllInboxMessagesResult.Builder builder = GetAllInboxMessagesResult.newBuilder();
      if (userInbox != null) {
        for (Message m : userInbox)
          builder.addMids(m.getId());
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
      Message m = hibernate.get(Message.class, request.getMid());
      if (m != null && m.getDestination() != null) {
        m.getDestination().remove(request.getName() + "@" + domain);
        hibernate.update(m);
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
      Message m = hibernate.get(Message.class, request.getMid());
      if (m != null) {
        hibernate.delete(m);
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
      String userEmail = request.getName() + "@" + domain;
      String queryStr = "SELECT m FROM Message m WHERE '" + userEmail + "' member of m.destination";
      List<Message> inbox = hibernate.jpql(queryStr, Message.class);

      SearchInboxResult.Builder builder = SearchInboxResult.newBuilder();
      if (inbox != null) {
        for (Message m : inbox) {
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