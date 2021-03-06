package lila.coach

import org.joda.time.DateTime
import scalaz.NonEmptyList

// contains aggregated data over (up to) 100 games
case class Period(
    _id: String, // random
    userId: String,
    data: UserStat,
    from: DateTime,
    to: DateTime,
    computedAt: DateTime) {

  def id = _id

  def merge(o: Period) = copy(
    data = data merge o.data,
    from = from,
    to = o.to)

  def isFresh = DateTime.now minusDays 1 isBefore computedAt
  def isStale = !isFresh

  def nbGames = data.results.base.nbGames
}

object Period {

  val MAX_GAMES = 100

  case class Computation(period: Period, data: UserStat.Computation) {

    def aggregate(p: RichPov) = copy(data = data aggregate p)

    def run = period.copy(data = data.run)

    def nbGames = data.nbGames
  }
  def initComputation(userId: String, pov: RichPov) =
    Computation(build(userId, pov), UserStat.emptyComputation aggregate pov)

  def build(userId: String, pov: RichPov) = Period(
    _id = ornicar.scalalib.Random nextStringUppercase 8,
    userId = userId,
    data = UserStat.empty,
    from = pov.pov.game.createdAt,
    to = pov.pov.game.createdAt,
    computedAt = DateTime.now)
}

case class Periods(periods: NonEmptyList[Period]) {

  lazy val period: Period = periods.tail.foldLeft(periods.head)(_ merge _)
}

object Periods {

  case class Computation(
      userId: String,
      save: Period => Funit,
      period: Option[Period.Computation]) {

    def aggregate(p: RichPov): Fu[Computation] = ((period, p) match {
      case (None, p) => fuccess {
        Period.initComputation(userId, p)
      }
      case (Some(comp), p) if comp.nbGames >= Period.MAX_GAMES => save(comp.run) inject {
        Period.initComputation(userId, p)
      }
      case (Some(comp), p) => fuccess {
        comp.aggregate(p)
      }
    }) map { comp =>
      copy(period = comp.some)
    }

    def run: Funit = period.filter(_.nbGames > 0) ?? { p => save(p.run) }
  }
  def initComputation(userId: String, save: Period => Funit) = Computation(userId, save, None)
}
