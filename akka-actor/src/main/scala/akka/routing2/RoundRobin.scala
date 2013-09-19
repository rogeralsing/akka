/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package akka.routing2

import java.util.concurrent.atomic.AtomicLong
import scala.collection.immutable
import akka.actor.ActorContext
import akka.actor.Props
import akka.dispatch.Dispatchers
import com.typesafe.config.Config
import akka.routing.RouterConfig
import akka.actor.SupervisorStrategy
import akka.japi.Util.immutableSeq

object RoundRobinRoutingLogic {
  def apply(): RoundRobinRoutingLogic = new RoundRobinRoutingLogic
}

class RoundRobinRoutingLogic extends RoutingLogic {
  val next = new AtomicLong(0)

  override def select(message: Any, routees: immutable.IndexedSeq[Routee]): Routee =
    if (routees.isEmpty) NoRoutee
    else routees((next.getAndIncrement % routees.size).asInstanceOf[Int])

}

final case class RoundRobinPool(
  override val nrOfInstances: Int, override val resizer2: Option[Resizer] = None,
  override val supervisorStrategy: SupervisorStrategy = RouterConfig2.defaultSupervisorStrategy,
  override val routerDispatcher: String = Dispatchers.DefaultDispatcherId)
  extends Pool with PoolOverrideUnsetConfig[RoundRobinPool] {

  def this(config: Config) =
    this(
      nrOfInstances = config.getInt("nr-of-instances"),
      resizer2 = DefaultResizer.fromConfig(config))

  override def createRouter(): Router = new Router(RoundRobinRoutingLogic())

  /**
   * Setting the supervisor strategy to be used for the “head” Router actor.
   */
  def withSupervisorStrategy(strategy: SupervisorStrategy): RoundRobinPool = copy(supervisorStrategy = strategy)

  /**
   * Setting the resizer to be used.
   */
  def withResizer(resizer: Resizer): RoundRobinPool = copy(resizer2 = Some(resizer))

  /**
   * Uses the resizer and/or the supervisor strategy of the given Routerconfig
   * if this RouterConfig doesn't have one, i.e. the resizer defined in code is used if
   * resizer was not defined in config.
   */
  override def withFallback(other: RouterConfig): RouterConfig = this.overrideUnsetConfig(other)

}

final case class RoundRobinNozzle(
  paths: immutable.Iterable[String],
  override val supervisorStrategy: SupervisorStrategy = RouterConfig2.defaultSupervisorStrategy,
  override val routerDispatcher: String = Dispatchers.DefaultDispatcherId)
  extends Nozzle with NozzleOverrideUnsetConfig[RoundRobinNozzle] {

  def this(config: Config) =
    this(paths = immutableSeq(config.getStringList("routees.paths")))

  override def createRouter(): Router = new Router(RoundRobinRoutingLogic())

  /**
   * Setting the supervisor strategy to be used for the “head” Router actor.
   */
  override def withSupervisorStrategy(strategy: SupervisorStrategy): RoundRobinNozzle = copy(supervisorStrategy = strategy)

  /**
   * Uses the resizer and/or the supervisor strategy of the given Routerconfig
   * if this RouterConfig doesn't have one, i.e. the resizer defined in code is used if
   * resizer was not defined in config.
   */
  override def withFallback(other: RouterConfig): RouterConfig = this.overrideUnsetConfig(other)

}