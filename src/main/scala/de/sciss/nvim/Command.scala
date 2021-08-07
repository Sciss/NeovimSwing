package de.sciss.nvim

import wvlet.airframe.msgpack.spi.Value
import wvlet.airframe.msgpack.spi.Value.{ArrayValue, StringValue}

//object API {
//  implicit val codec: Codec[API] = CodecImpl // msgpack.serializeCodec[API]
//
//  private object CodecImpl extends Codec[API] {
//    override def decode(bits: BitVector): Attempt[DecodeResult[API]] = ???
//
//    override def encode(value: API): Attempt[BitVector] =
//      value.encode
//
//    override def sizeBound: SizeBound = SizeBound.unknown
//  }
//}
//sealed trait API {
//  def encode: Attempt[BitVector]
//}

object API {
//  implicit val codec: Codec[API] = CodecImpl // msgpack.serializeCodec[API]
//
//  private object CodecImpl extends Codec[API] {
//    override def decode(bits: BitVector): Attempt[DecodeResult[API]] = ???
//
//    override def encode(value: API): Attempt[BitVector] =
//      value.encode
//
//    override def sizeBound: SizeBound = SizeBound.unknown
//  }
}
sealed trait API {
  //  def encode: Attempt[BitVector]
//  def encode(p: Packer): Unit
}

sealed trait NotificationOrRequest extends API {
  def method: String
  def params: ArrayValue
}

sealed trait Notification extends NotificationOrRequest

sealed trait Request[A] extends NotificationOrRequest {
  def decode(v: Value): A
}

object Command {

}
final case class Command(s: String) extends Notification {
  def method: String = "nvim_command"

  override def params: ArrayValue =
    ArrayValue(StringValue(s) +: Vector.empty)

//  override def encode(p: Packer): Unit = {
////    val m = MFixArray(MUInt8(2) +: MFixString("nvim_command") +: MString16(s) +: Vector.empty)
////    MessagePackCodec.encode(m)
//
//    p.packArrayHeader(3)
//    p.packInt(2)
//    p.packString("nvim_command")
//    p.packArrayHeader(1)
//    p.packString(s)
//  }
}

final case class Eval(s: String) extends Request[String] {
  override def method: String = "nvim_eval"

  override def params: ArrayValue =
    ArrayValue(StringValue(s) +: Vector.empty)

  override def decode(v: Value): String = v match {
    case StringValue(s) => s
    case other          => other.toString
  }
}