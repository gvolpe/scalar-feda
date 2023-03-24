import java.time.Instant
import java.util.UUID

import cats.Applicative
import cats.effect.*
import cats.syntax.all.*
import fs2.Stream

object ComplexEventProcessing:
  type EventId   = UUID
  type Price     = BigDecimal
  type Symbol    = String
  type Timestamp = Instant

  def runner(events: Stream[IO, PriceEvent | Tick]): IO[Unit] =
    val alerts = PriceAlerts.make(List("EURUSD", "EURGBP"))
    val engine = PriceEngine2(alerts)
    events.evalMapAccumulate(PriceState.empty)(engine.fsm.run).compile.drain

  case object Tick
  type Tick = Tick.type

  type UserEmail = String

  enum UserEvent:
    case Registered(id: EventId, email: UserEmail)
    case Deleted(id: EventId, email: UserEmail)

  case class UserState(emails: Set[UserEmail])

  enum PriceEvent:
    case Created(symbol: Symbol, price: Price, createdAt: Timestamp)
    case Updated(symbol: Symbol, price: Price, createdAt: Timestamp)
    case Deleted(symbol: Symbol, createdAt: Timestamp)

  case class PriceHistory(
      history: List[(Price, Timestamp)]
  )

  object PriceHistory:
    def empty: PriceHistory = PriceHistory(List.empty)
    def init(price: Price, ts: Timestamp): PriceHistory =
      PriceHistory(List(price -> ts))

  case class PriceState(prices: Map[Symbol, PriceHistory])

  object PriceState:
    def empty: PriceState = PriceState(Map.empty)

  type St[In] = In match
    case PriceEvent | Tick => PriceState
    case UserEvent         => UserState

  type Output[In] = In match
    case PriceEvent | Tick => Unit
    case UserEvent         => Int

  type SM[F[_], In] = FSM[F, St[In], In, Output[In]]

  trait PriceAlerts[F[_]]:
    def publish(prices: Map[Symbol, PriceHistory]): F[Unit]

  object PriceAlerts:
    def make(symbols: List[Symbol]): PriceAlerts[IO] = new:
      def publish(prices: Map[Symbol, PriceHistory]): IO[Unit] =
        prices.view.filterKeys(symbols.contains).toList.traverse_ { (symbol, history) =>
          IO.println(s">>> Symbol: $symbol - History: $history")
        }

  object PriceEngine1:
    val fsm = FSM.id[PriceState, PriceEvent, Unit] {
      case (st, PriceEvent.Created(symbol, price, ts)) =>
        PriceState(st.prices.updated(symbol, PriceHistory.init(price, ts))) -> ()
      case (st, PriceEvent.Updated(symbol, price, ts)) =>
        val current = st.prices.get(symbol).toList.flatMap(_.history)
        val updated = PriceHistory(current ::: List(price -> ts))
        PriceState(st.prices.updated(symbol, updated)) -> ()
      case (st, PriceEvent.Deleted(symbol, ts)) =>
        PriceState(st.prices.removed(symbol)) -> ()
    }

  class PriceEngine2[F[_]: Applicative](alerts: PriceAlerts[F]):
    val fsm = FSM[F, PriceState, PriceEvent | Tick, Unit] {
      case (st, PriceEvent.Created(symbol, price, ts)) =>
        (PriceState(st.prices.updated(symbol, PriceHistory.init(price, ts))) -> ()).pure[F]
      case (st, PriceEvent.Updated(symbol, price, ts)) =>
        val current = st.prices.get(symbol).toList.flatMap(_.history)
        val updated = PriceHistory(current ::: List(price -> ts))
        (PriceState(st.prices.updated(symbol, updated)) -> ()).pure[F]
      case (st, PriceEvent.Deleted(symbol, ts)) =>
        (PriceState(st.prices.removed(symbol)) -> ()).pure[F]
      case (st, Tick) =>
        alerts.publish(st.prices).tupleLeft(PriceState.empty)
    }

    val fsm3: SM[F, PriceEvent | Tick] = fsm

    val fsm4: SM[F, UserEvent] = FSM {
      case (st, UserEvent.Registered(_, email)) =>
        val nst = UserState(st.emails + email)
        (nst -> nst.emails.size).pure[F]
      case (st, UserEvent.Deleted(_, email)) =>
        val nst = UserState(st.emails - email)
        (nst -> nst.emails.size).pure[F]
    }
