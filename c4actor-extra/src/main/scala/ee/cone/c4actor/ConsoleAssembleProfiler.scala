package ee.cone.c4actor

import com.typesafe.scalalogging.LazyLogging
import ee.cone.c4assemble.{Join, SerialJoiningProfiling, WorldTransition}
import ee.cone.c4assemble.Types.{DPIterable, Index}

import scala.collection.immutable

case object ConsoleAssembleProfiler extends AssembleProfiler {
  def createSerialJoiningProfiling(localOpt: Option[Context]): SerialJoiningProfiling = ConsoleProfiling

  def addMeta(profiling: SerialJoiningProfiling, updates: immutable.Seq[QProtocol.Update]): immutable.Seq[QProtocol.Update] = updates
}

case object ConsoleProfiling extends SerialJoiningProfiling with LazyLogging {
  def time: Long = System.nanoTime

  def handle(
    join: Join,
    calcStart: Long,
    findChangesStart: Long,
    patchStart: Long,
    joinRes: DPIterable[Index],
    transition: WorldTransition
  ): WorldTransition = {
    val timeNano: Long = (System.nanoTime - calcStart) / 10000
    val timeFront: Double = timeNano / 100.0
    val countT = joinRes.size
    logger.debug(s"rule ${join.assembleName}-${join.name} ${getColoredCount(countT)} items for ${getColoredPeriod(timeFront)} ms")
    transition
  }

  def getColoredPeriod: Double ⇒ String = {
    case i if i < 200 ⇒ PrintColored.makeColored("g")(i.toString)
    case i if i >= 200 && i < 500 ⇒ PrintColored.makeColored("y")(i.toString)
    case i if i >= 500 ⇒ PrintColored.makeColored("r")(i.toString)
  }

  def getColoredCount: Int ⇒ String = {
    case i if i < 100 ⇒ PrintColored.makeColored("g")(i.toString)
    case i if i >= 100 && i < 1000 ⇒ PrintColored.makeColored("y")(i.toString)
    case i if i >= 1000 ⇒ PrintColored.makeColored("r")(i.toString)
  }

}