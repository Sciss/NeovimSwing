package de.sciss.nvim

import scodec.bits.BitVector
import scodec.msgpack.SerializeAuto._
import scodec.msgpack._
import scodec.{Attempt, Codec, DecodeResult}

sealed abstract class Setting extends Product with Serializable
final case class IsCompact(v: Boolean) extends Setting
final case class SchemaSize(v: Int) extends Setting
final case class MsgPackExample(compact: IsCompact, schema: SchemaSize)

object CaseClassExample {
  def main(args: Array[String]): Unit = run()

  def run(): Unit = {
    val input = MsgPackExample(IsCompact(true), SchemaSize(123))
    roundTrip(input)
    println("Ok")
  }

  def roundTrip[A](a: A)(implicit C: Codec[A]): Unit = {
    C.encode(a) match {
      case Attempt.Failure(error) =>
        sys.error(error.toString())
      case Attempt.Successful(encoded) =>
        C.decode(encoded) match {
          case Attempt.Failure(error) =>
            sys.error(error.toString())
          case Attempt.Successful(DecodeResult(decoded, remainder)) =>
            assert(remainder === BitVector.empty)
            assert(decoded == a)
        }
    }
  }
}