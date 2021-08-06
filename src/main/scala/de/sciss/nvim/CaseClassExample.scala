package de.sciss.nvim

import scodec.bits.BitVector
import scodec.msgpack.SerializeAuto._
import scodec.msgpack._
import scodec.{Attempt, Codec, DecodeResult}

sealed abstract class Setting extends Product with Serializable
final case class IsCompact  (v: Boolean ) extends Setting
final case class SchemaSize (v: Int     ) extends Setting
final case class MsgPackExample(compact: IsCompact, schema: SchemaSize)

object CaseClassExample {
  def main(args: Array[String]): Unit = run()

  def run(): Unit = {
    val in = MsgPackExample(IsCompact(true), SchemaSize(123))
    roundTrip(in)
    println("Ok")
  }

  def roundTrip[A](a: A)(implicit cdc: Codec[A]): Unit = {
    cdc.encode(a) match {
      case Attempt.Failure(e) =>
        sys.error(e.toString)
      case Attempt.Successful(enc) =>
        cdc.decode(enc) match {
          case Attempt.Failure(e) =>
            sys.error(e.toString)
          case Attempt.Successful(DecodeResult(dec, rem)) =>
            assert(rem === BitVector.empty)
            assert(dec == a)
        }
    }
  }
}