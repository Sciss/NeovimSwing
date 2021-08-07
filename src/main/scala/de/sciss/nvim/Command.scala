package de.sciss.nvim

import scodec.bits.BitVector
import scodec.msgpack.codecs.MessagePackCodec
import scodec.msgpack.{MFixArray, MFixString, MString16, MUInt8}
import scodec.{Attempt, Codec, DecodeResult, SizeBound}

object API {
  implicit val codec: Codec[API] = CodecImpl // msgpack.serializeCodec[API]

  private object CodecImpl extends Codec[API] {
    override def decode(bits: BitVector): Attempt[DecodeResult[API]] = ???

    override def encode(value: API): Attempt[BitVector] =
      value.encode

    override def sizeBound: SizeBound = SizeBound.unknown
  }
}
sealed trait API {
  def encode: Attempt[BitVector]
}

object Command {

}
case class Command(s: String) extends API {
  override def encode: Attempt[BitVector] = {
    val m = MFixArray(MUInt8(2) +: MFixString("nvim_command") +: MString16(s) +: Vector.empty)
    MessagePackCodec.encode(m)
  }
}