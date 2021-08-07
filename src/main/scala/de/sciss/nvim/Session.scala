package de.sciss.nvim

import wvlet.airframe.msgpack.spi.{MessagePack, Value}
import wvlet.airframe.msgpack.spi.Value.StringValue

import java.io.{InputStream, OutputStream}
import scala.annotation.switch
import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal

object PacketType {
  final val Request       = 0
  final val Response      = 1
  final val Notification  = 2
}

// Provided by the user on session start to register any msgpack extended types
case class ExtendedType[A <: AnyRef](typeClass: Class[A], typeId: Byte,
                                     serializer: A => Array[Byte],
                                     deserializer: Array[Byte] => A)

object Session {
  def apply(in: InputStream, out: OutputStream): Session =
    new Impl(in, out)

  private final class Impl(in: InputStream, out: OutputStream)
    extends Session {

    private[this] var nextReqId = 1
    private[this] val reqMap    = mutable.Map.empty[Int, Req[_]]

    private[this] val sync = new AnyRef

    private final class Req[A](/*val id: Int,*/ pr: Promise[A], peer: Request[A]) {
      def complete(v: Value): Unit =
        try {
          val resT = peer.decode(v)
          pr.success(resT)
        } catch {
          case NonFatal(e) =>
            pr.failure(e)
        }

      def fail(message: String): Unit =
        pr.failure(new Exception(message))
    }

    // Create a thread to listen for any packets
    new Thread {
      private[this] val u = MessagePack.newUnpacker(in)

      override def run(): Unit =
        try {
          while (true) {
            if (u.hasNext) {
              val sz  = u.unpackArrayHeader
              val tpe = u.unpackInt
              (tpe: @switch) match {
                case PacketType.Request =>
                  // [type, msgid, method, params]
                  require (sz == 4)
                  val msgId   = u.unpackInt
                  val method  = u.unpackString
                  val args    = u.unpackValue
                  println(s"Request: $msgId, $method, $args")

                case PacketType.Response =>
                  // [type, msgid, error, result]
                  require (sz == 4)
                  val msgId = u.unpackInt
                  val ok    = u.tryUnpackNil
                  sync.synchronized {
                    reqMap.remove(msgId) match {
                      case Some(req) =>
                        if (ok) {
                          val res = u.unpackValue
                          println(s"Response: $msgId, success - $res")
                          req.complete(res)

                        } else {
                          val errV  = u.unpackValue
                          val err   = errV match {
                            case StringValue(s) => s
                            case other          => other.toString
                          }
                          /*val res   =*/ u.unpackValue
                          println(s"Response: $msgId, failure - $err")
                          req.fail(err)
                        }
                      case None =>
                        println(s"Ooops. No handler for request $msgId")
                    }
                  }

                case PacketType.Notification =>
                  // [type, method, params]
                  require (sz == 3)
                  val method  = u.unpackString
                  val args    = u.unpackValue
                  println(s"Notification: $method, $args")
              }

            } else {
              Thread.sleep(4) // XXX TODO can we run asynchronously?
            }
          }
        } catch {
          case NonFatal(e) =>
            e.printStackTrace()
        }

      start()
    }

    override def request[A](req: Request[A]): Future[A] =
      sync.synchronized {
        val id = nextReqId
        nextReqId += 1

        val pr = Promise[A]()
        val rq = new Req(pr, req)
        assert (reqMap.put(id, rq).isEmpty)

        val p = MessagePack.newBufferPacker
        p.packArrayHeader(4)
        p.packInt(PacketType.Request)
        p.packInt(id)
        p.packString(req.method)
        p.packValue(req.params)
        out.write(p.toByteArray)
        out.flush()

        pr.future
      }

    override def notify(n: Notification): Unit =
      sync.synchronized {
//      val p = MessagePack.newPacker(out) -- N.B. seems broken
        val p = MessagePack.newBufferPacker
        p.packArrayHeader(3)
        p.packInt(PacketType.Notification)
        p.packString(n.method)
        p.packValue(n.params)
        out.write(p.toByteArray)
        out.flush()
      }
  }
}
trait Session {
  def notify(n: Notification): Unit

  def request[A](req: Request[A]): Future[A]
}