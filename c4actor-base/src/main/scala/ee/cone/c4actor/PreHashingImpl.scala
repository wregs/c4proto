package ee.cone.c4actor

// http://www.artima.com/pins1ed/object-equality.html
object PreHashingImpl extends PreHashing {
  def wrap[T](value: T): PreHashed[T] = new PreHashedImpl(value.hashCode,value)
}
final class PreHashedImpl[T](code: Int, val value: T) extends PreHashed[T] {
  override def hashCode: Int = code
  override def equals(that: Any): Boolean = that match {
    case that: PreHashed[_] ⇒ value == that.value
    case _ => false
  }
  override def toString: String = s"PreHashed(${value.toString})"
}

