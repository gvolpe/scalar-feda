import java.time.Instant

import scala.concurrent.duration.*
import scala.util.Random

import cats.effect.*
import fs2.Stream

object ScalarTalk extends IOApp.Simple:
  import ComplexEventProcessing.*

  val events: List[PriceEvent | Tick] = List(
    PriceEvent.Created("EURUSD", BigDecimal(Random.nextInt(10)), Instant.now()),
    PriceEvent.Updated("EURUSD", BigDecimal(Random.nextInt(10)), Instant.now()),
    PriceEvent.Created("EURGBP", BigDecimal(Random.nextInt(10)), Instant.now()),
    Tick,
    PriceEvent.Created("EURPLN", BigDecimal(Random.nextInt(10)), Instant.now()),
    PriceEvent.Updated("EURGBP", BigDecimal(Random.nextInt(10)), Instant.now()),
    PriceEvent.Updated("EURUSD", BigDecimal(Random.nextInt(10)), Instant.now()),
    PriceEvent.Deleted("EURUSD", Instant.now()),
    Tick
  )

  // val ticks: Stream[IO, Tick] =
  // Stream.fixedDelay[IO](2.seconds).as(Tick)

  // val events2: Stream[IO, PriceEvent] = ???

  // val foo: IO[Unit] = runner(events2.merge(ticks))

  def run: IO[Unit] =
    // assume events come from a Pulsar topic
    runner(Stream.emits(events).metered(300.millis))
