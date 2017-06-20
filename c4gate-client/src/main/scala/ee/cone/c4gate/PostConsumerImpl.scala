package ee.cone.c4gate

import ee.cone.c4actor.Types.SrcId
import ee.cone.c4actor._
import ee.cone.c4assemble.Types.Values
import ee.cone.c4assemble.{Assemble, assemble, by}
import ee.cone.c4gate.AlienProtocol.PostConsumer

@assemble class PostConsumerAssemble(actorName: ActorName) extends Assemble {

  type WasConsumer = SrcId
  def wasConsumers(
    key: SrcId,
    consumers: Values[PostConsumer]
  ): Values[(WasConsumer,PostConsumer)] =
    for(c ← consumers if c.consumer == actorName.value) yield WithSrcId(c)

  type NeedConsumer = SrcId
  def needConsumers(
    key: SrcId,
    consumers: Values[LocalPostConsumer]
  ): Values[(NeedConsumer,PostConsumer)] =
    for(c ← consumers)
      yield WithSrcId(PostConsumer(s"${actorName.value}/${c.condition}", actorName.value, c.condition))

  def syncConsumers(
    key: SrcId,
    @by[WasConsumer] wasConsumers: Values[PostConsumer],
    @by[NeedConsumer] needConsumers: Values[PostConsumer]
  ): Values[(SrcId,TxTransform)] =
    if(wasConsumers.toList == needConsumers.toList) Nil
    else List(WithSrcId(SimpleTxTransform(key,
      wasConsumers.flatMap(LEvent.delete) ++ needConsumers.flatMap(LEvent.update)
    )))
}
