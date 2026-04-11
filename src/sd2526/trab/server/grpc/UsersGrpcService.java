package sd2526.trab.server.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import sd2526.trab.api.User;
import sd2526.trab.api.grpc.GrpcUsersGrpc;
import sd2526.trab.api.grpc.Users.*;
import sd2526.trab.server.persistence.Hibernate;
import java.util.List;

public class UsersGrpcService extends GrpcUsersGrpc.GrpcUsersImplBase {
  private final Hibernate hibernate = Hibernate.getInstance();
  private final String domain;

  public UsersGrpcService(String domain) {
    this.domain = domain;
  }

  private GrpcUser toGrpcUser(User u) {
    GrpcUser.Builder builder = GrpcUser.newBuilder();

    if (u.getName() != null)
      builder.setName(u.getName());
    if (u.getPwd() != null)
      builder.setPwd(u.getPwd());

    builder.setDomain(u.getDomain() != null ? u.getDomain() : domain);

    if (u.getDisplayName() != null) {
      builder.setDisplayName(u.getDisplayName());
    }

    return builder.build();
  }

  @Override
  public void postUser(GrpcUser request, StreamObserver<PostUserResult> responseObserver) {
    try {
      if (request.getName().isEmpty() || !request.hasPwd() || request.getPwd().isEmpty()) {
        responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Invalid data").asRuntimeException());
        return;
      }
      User user = hibernate.get(User.class, request.getName());
      if (user != null) {
        if (!user.getPwd().equals(request.getPwd())) {
          responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Wrong password").asRuntimeException());
          return;
        }
        String existingDisplay = user.getDisplayName() == null ? "" : user.getDisplayName();
        String reqDisplay = request.hasDisplayName() ? request.getDisplayName() : "";

        if (request.hasDisplayName() && !existingDisplay.equals(reqDisplay) && !reqDisplay.isEmpty()) {
          responseObserver.onError(Status.ALREADY_EXISTS.withDescription("Conflict").asRuntimeException());
          return;
        }
      } else {
        String dispName = (request.hasDisplayName() && !request.getDisplayName().isEmpty()) ? request.getDisplayName()
            : request.getName();
        user = new User(request.getName(), request.getPwd(), dispName, domain);
        hibernate.persist(user);
      }
      responseObserver.onNext(PostUserResult.newBuilder().setUserAddress(user.getName() + "@" + domain).build());
      responseObserver.onCompleted();
    } catch (Exception e) {
      responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void getUser(GetUserArgs request, StreamObserver<GetUserResult> responseObserver) {
    try {
      User user = hibernate.get(User.class, request.getName());
      if (user == null || !user.getPwd().equals(request.getPwd())) {
        responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Forbidden").asRuntimeException());
        return;
      }
      responseObserver.onNext(GetUserResult.newBuilder().setUser(toGrpcUser(user)).build());
      responseObserver.onCompleted();
    } catch (Exception e) {
      responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void updateUser(UpdateUserArgs request, StreamObserver<UpdateUserResult> responseObserver) {
    try {
      User user = hibernate.get(User.class, request.getName());
      if (user == null || !user.getPwd().equals(request.getPwd())) {
        responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Forbidden").asRuntimeException());
        return;
      }
      GrpcUser info = request.getInfo();

      // Validate name/domain changes
      if (!info.getName().isEmpty() && !info.getName().equals(request.getName())) {
        responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Cannot change name").asRuntimeException());
        return;
      }
      if (info.hasDomain() && !info.getDomain().isEmpty() && !info.getDomain().equals(domain)) {
        responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Cannot change domain").asRuntimeException());
        return;
      }

      if (info.hasPwd() && !info.getPwd().isEmpty()) {
        user.setPwd(info.getPwd());
      }

      if (info.hasDisplayName() && !info.getDisplayName().isEmpty()) {
        user.setDisplayName(info.getDisplayName());
      }

      hibernate.update(user);
      responseObserver.onNext(UpdateUserResult.newBuilder().setUser(toGrpcUser(user)).build());
      responseObserver.onCompleted();
    } catch (Exception e) {
      responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void deleteUser(DeleteUserArgs request, StreamObserver<DeleteUserResult> responseObserver) {
    try {
      User user = hibernate.get(User.class, request.getName());
      if (user == null || !user.getPwd().equals(request.getPwd())) {
        responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Forbidden").asRuntimeException());
        return;
      }
      hibernate.delete(user);
      responseObserver.onNext(DeleteUserResult.newBuilder().setUser(toGrpcUser(user)).build());
      responseObserver.onCompleted();
    } catch (Exception e) {
      responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void searchUsers(SearchUsersArgs request, StreamObserver<GrpcUser> responseObserver) {
    try {
      User user = hibernate.get(User.class, request.getName());
      if (user == null || !user.getPwd().equals(request.getPwd())) {
        responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Forbidden").asRuntimeException());
        return;
      }
      List<User> all = hibernate.jpql("SELECT u FROM User u", User.class);
      for (User u : all) {
        if (request.getQuery().isEmpty() || u.getName().toLowerCase().contains(request.getQuery().toLowerCase())) {
          responseObserver.onNext(toGrpcUser(u));
        }
      }
      responseObserver.onCompleted();
    } catch (Exception e) {
      responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }
  }
}