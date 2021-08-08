/*
 * Session.scala
 * (NeovimSwing)
 *
 * Copyright (c) 2021 Hanns Holger Rutz. All rights reserved.
 *
 * This software is published under the GNU Lesser General Public License v2.1+
 *
 *
 * For further information, please contact Hanns Holger Rutz at
 * contact@sciss.de
 */

package de.sciss.nvim

import de.sciss.model.Model
import de.sciss.model.impl.ModelImpl
import wvlet.airframe.msgpack.spi.{MessagePack, Value}
import wvlet.airframe.msgpack.spi.Value.StringValue

import java.io.{InputStream, OutputStream}
import scala.annotation.switch
import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal

object Session {
  def apply(in: InputStream, out: OutputStream): Session =
    new Impl(in, out)

  private final class Impl(in: InputStream, out: OutputStream)
    extends Session with ModelImpl[Packet] {

    private final val DEBUG = false

    private[this] var nextReqId   = 1
    private[this] val handlerMap  = mutable.Map.empty[Int, Handler[_]]

    private[this] val sync = new AnyRef

    private final class Handler[A](pr: Promise[A], peer: Request[A]) {
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
        while (u.hasNext) {
          try {
            val sz  = u.unpackArrayHeader
            val tpe = u.unpackInt
            (tpe: @switch) match {
              case Packet.RequestType =>
                // [type, msgid, method, params]
                require (sz == 4)
                val msgId   = u.unpackInt
                val method  = u.unpackString
                val params  = u.unpackValue
                if (DEBUG) println(s"Request: $msgId, $method, $params")

              case Packet.ResponseType =>
                // [type, msgid, error, result]
                require (sz == 4)
                val msgId = u.unpackInt
                val ok    = u.tryUnpackNil
                sync.synchronized {
                  handlerMap.remove(msgId) match {
                    case Some(req) =>
                      if (ok) {
                        val result = u.unpackValue
                        if (DEBUG) println(s"Response: $msgId, success - $result")
                        req.complete(result)

                      } else {
                        val errV  = u.unpackValue
                        val error = errV match {
                          case StringValue(s) => s
                          case other          => other.toString
                        }
                        /*val res   =*/ u.unpackValue
                        if (DEBUG) println(s"Response: $msgId, failure - $error")
                        req.fail(error)
                      }
                    case None =>
                      println(s"Ooops. No handler for request $msgId")
                  }
                }

              case Packet.NotificationType =>
                // [type, method, params]
                require (sz == 3)
                val method  = u.unpackString
                val params  = u.unpackValue
                if (DEBUG) println(s"Notification: $method, $params")
                Notification.get(method) match {
                  case Some(nf) =>
                    val n = nf.decode(params)
                    dispatch(n)

                  case None =>
                }
            }
          } catch {
            case NonFatal(e) =>
              e.printStackTrace()
          }
        }

      start()
    }

    // XXX TODO: timeout
    override def !![A](req: Request[A], timeout: Duration): Future[A] =
      sync.synchronized {
        val id = nextReqId
        nextReqId += 1

        val pr = Promise[A]()
        val rq = new Handler(pr, req)
        assert (handlerMap.put(id, rq).isEmpty)

        val p = MessagePack.newBufferPacker
        p.packArrayHeader(4)
        p.packInt(Packet.RequestType)
        p.packInt(id)
        p.packString(req.method)
        p.packValue(req.params)
        out.write(p.toByteArray)
        out.flush()

        pr.future
      }

    override def !(n: Notification): Unit =
      sync.synchronized {
//      val p = MessagePack.newPacker(out) -- N.B. seems broken
        val p = MessagePack.newBufferPacker
        p.packArrayHeader(3)
        p.packInt(Packet.NotificationType)
        p.packString(n.method)
        p.packValue(n.params)
        out.write(p.toByteArray)
        out.flush()
      }
  }
}
trait Session extends Model[Packet] {
  def ! (n: Notification): Unit

  def !! [A](req: Request[A], timeout: Duration): Future[A]
}