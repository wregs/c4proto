
package ee.cone.c4actor

import com.squareup.wire.ProtoAdapter
import ee.cone.c4actor.QProtocol.{TxRef, Update, Updates}
import ee.cone.c4proto.{HasId, Protocol, ToByteString}

import scala.collection.immutable.{Queue, Seq}
import java.nio.charset.StandardCharsets.UTF_8

import ee.cone.c4actor.Types.NextOffset
import okio.ByteString

/*Future[RecordMetadata]*/
//producer.send(new ProducerRecord(topic, rawKey, rawValue))
//decode(new ProtoReader(new okio.Buffer().write(bytes)))
//

class KafkaHeaderImpl(val key: String,val value: Array[Byte]) extends KafkaHeader

class QRecordImpl(val topic: TopicName, val value: Array[Byte], val headers: Seq[KafkaHeader]) extends QRecord

class QMessagesImpl(toUpdate: ToUpdate, getRawQSender: ()⇒RawQSender, defaultCompressor: Compressor) extends QMessages {
  //import qAdapterRegistry._
  // .map(o⇒ nTx.setLocal(OffsetWorldKey, o+1))
  def send[M<:Product](local: Context): Context = {
    val currentCompressor = CurrentCompressorKey.of(local).getOrElse(defaultCompressor)
    val updates: List[Update] = WriteModelKey.of(local).toList
    if(updates.isEmpty) return local
    //println(s"sending: ${updates.size} ${updates.map(_.valueTypeId).map(java.lang.Long.toHexString)}")
    val rec = new QRecordImpl(InboxTopicName(),toUpdate.toBytes(updates, currentCompressor), currentCompressor.getKafkaHeaders)
    val debugStr = WriteModelDebugKey.of(local).map(_.toString).mkString("\n---\n")
    val debugRec = new QRecordImpl(LogTopicName(),debugStr.getBytes(UTF_8), Nil)
    val List(offset,_)= getRawQSender().send(List(rec,debugRec))
    Function.chain(Seq(
      WriteModelKey.set(Queue.empty),
      WriteModelDebugKey.set(Queue.empty),
      ReadAfterWriteOffsetKey.set(offset)
    ))(local)
  }
}

class ToUpdateImpl(qAdapterRegistry: QAdapterRegistry, compressorRegistry: CompressorRegistry)(
  updatesAdapter: ProtoAdapter[Updates] with HasId =
    qAdapterRegistry.byName(classOf[QProtocol.Updates].getName)
    .asInstanceOf[ProtoAdapter[Updates] with HasId],
  refAdapter: ProtoAdapter[TxRef] with HasId =
    qAdapterRegistry.byName(classOf[TxRef].getName)
    .asInstanceOf[ProtoAdapter[TxRef] with HasId]
) extends ToUpdate {
  def toUpdate[M <: Product](message: LEvent[M]): Update = {
    val valueAdapter = qAdapterRegistry.byName(message.className)
    val byteString = ToByteString(message.value.map(valueAdapter.encode).getOrElse(Array.empty))
    Update(message.srcId, valueAdapter.id, byteString)
  }

  private def findCompressor: List[RawHeader] ⇒ Compressor = list ⇒
    compressorRegistry.byName(
      list.collectFirst { case header if header.key == "compressor" ⇒ new String(header.data.toByteArray,UTF_8) }.getOrElse("")
    )


  def toBytes(updates: List[Update], compressor: Compressor): Array[Byte] =
    compressor.compressRaw(updatesAdapter.encode(Updates("", updates)))

  def toUpdates(events: List[RawEvent]): List[Update] =
    for {
      event ← events
      a ← {println(event.srcId, event.headers, event.data.size , findCompressor(event.headers)); 1 :: Nil}
      compressor = findCompressor(event.headers)
      update ← updatesAdapter.decode(compressor.deCompress(event.data)).updates
    } yield
      if (update.valueTypeId != refAdapter.id) update
      else {
        val ref: TxRef = refAdapter.decode(update.value)
        if (ref.txId.nonEmpty) update
        else update.copy(value = ToByteString(refAdapter.encode(ref.copy(txId = event.srcId))))
      }


  def toKey(up: Update): Update = up.copy(value=ByteString.EMPTY)
  def by(up: Update): (Long, String) = (up.valueTypeId,up.srcId)
}

object QAdapterRegistryFactory {
  def apply(protocols: List[Protocol]): QAdapterRegistry = {
    val adapters = protocols.flatMap(_.adapters).asInstanceOf[List[ProtoAdapter[Product] with HasId]]
    val byName = CheckedMap(adapters.map(a ⇒ a.className → a))
    val byId = CheckedMap(adapters.filter(_.hasId).map(a ⇒ a.id → a))
    new QAdapterRegistry(byName, byId)
  }
}

class LocalQAdapterRegistryInit(qAdapterRegistry: QAdapterRegistry) extends ToInject {
  def toInject: List[Injectable] = QAdapterRegistryKey.set(qAdapterRegistry)
}

/*object NoRawQSender extends RawQSender {
  def send(recs: List[QRecord]): List[NextOffset] = Nil
}*/