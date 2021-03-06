package ee.cone.c4actor

import ee.cone.c4proto.{Id, protocol}

trait AbstractMetaAttr extends Product

case class MetaAttr(orig: Product) extends AbstractMetaAttr

@protocol object MetaAttrProtocolBase   {
  @Id(0x00ad) case class D_TxTransformNameMeta(
    @Id(0x00ae) clName: String
  )
}