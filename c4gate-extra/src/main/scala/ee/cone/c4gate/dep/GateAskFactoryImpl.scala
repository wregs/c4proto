package ee.cone.c4gate.dep

import java.util.UUID

import com.squareup.wire.ProtoAdapter
import ee.cone.c4actor._
import ee.cone.c4actor.dep.ContextTypes.ContextId
import ee.cone.c4actor.dep.{AskByPK, CommonRequestUtilityFactory, Dep}
import ee.cone.c4actor.dep_impl.RequestDep
import ee.cone.c4gate.SessionAttr
import ee.cone.c4gate.SessionDataProtocol.RawSessionData
import ee.cone.c4gate.dep.request.CurrentTimeRequestProtocol.CurrentTimeRequest
import ee.cone.c4proto.{HasId, ToByteString}
import okio.ByteString

case class SessionAttrAskFactoryImpl(
  qAdapterRegistry: QAdapterRegistry,
  defaultModelRegistry: DefaultModelRegistry,
  modelAccessFactory: ModelAccessFactory,
  commonRequestFactory: CommonRequestUtilityFactory,
  rawDataAsk: AskByPK[RawSessionData],
  uuidUtil: UUIDUtil
) extends SessionAttrAskFactoryApi {

  def askSessionAttr[P <: Product](attr: SessionAttr[P]): Dep[Option[Access[P]]] = {
    val adapter: ProtoAdapter[Product] with HasId = qAdapterRegistry.byName(classOf[RawSessionData].getName)

    def genPK(request: RawSessionData): String =
      uuidUtil.srcIdFromSerialized(adapter.id,ToByteString(adapter.encode(request)))

    val lens = ProdLens[RawSessionData, P](attr.metaList)(
      rawData ⇒ qAdapterRegistry.byId(rawData.valueTypeId).decode(rawData.value).asInstanceOf[P],
      value ⇒ rawData ⇒ {
        val valueAdapter = qAdapterRegistry.byName(attr.className)
        val byteString = ToByteString(valueAdapter.encode(value))
        rawData.copy(valueTypeId = valueAdapter.id, value = byteString)
      }
    )

    def rawSessionData: ContextId ⇒ RawSessionData = contextId ⇒
      RawSessionData(
        srcId = "",
        sessionKey = contextId,
        domainSrcId = attr.pk,
        fieldId = attr.id,
        valueTypeId = 0,
        value = ByteString.EMPTY
      )

    import commonRequestFactory._
    for {
      contextId ← askContextId
      rawModel ← rawDataAsk.option(genPK(rawSessionData(contextId)))
    } yield {
      val request = rawSessionData(contextId)
      val pk = genPK(request)

      val value = rawModel.getOrElse({
        val model = defaultModelRegistry.get[P](attr.className).create(pk)
        lens.set(model)(request.copy(srcId = pk))
      }
      )
      modelAccessFactory.to(value).map(_.to(lens))
    }
  }
}

case object CurrentTimeAskFactoryImpl extends CurrentTimeAskFactoryApi {
  def askCurrentTime(eachNSeconds: Long): Dep[Long] = new RequestDep[Long](CurrentTimeRequest(eachNSeconds))
}
