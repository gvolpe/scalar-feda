import java.util.UUID

object SimpleEventProcessing:
  type EventId = UUID
  type UserEmail = String

  enum UserEvent:
    case Registered(id: EventId, email: UserEmail)
    case Deleted(id: EventId, email: UserEmail)

  trait UserCache[F[_]]:
    def registerUser(email: UserEmail): F[Unit]
    def deleteUser(email: UserEmail): F[Unit]

  class UserEngine[F[_]](cache: UserCache[F]):
    def run: UserEvent => F[Unit] =
      case UserEvent.Registered(_, email) =>
        cache.registerUser(email)
      case UserEvent.Deleted(_, email) =>
        cache.deleteUser(email)
