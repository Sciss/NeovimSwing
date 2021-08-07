// originally based on https://github.com/fuyumatsuri/msgpack-rpc-scala/
// (BSD licensed)

package de.sciss.nvim

import java.io.{InputStream, OutputStream}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.reflect.ClassTag

object PacketType {
  final val Request       = 0
  final val Response      = 1
  final val Notification  = 2
}

abstract sealed class Packet

case class Request(packetType: Int, requestId: Long, method: String, args: List[Any]) extends Packet
object Request {
  def apply(requestId: Long, method: String, args: List[Any]) =
    new Request(PacketType.Request, requestId, method, args)
}

case class Response(packetType: Int, requestId: Long, error: Any, result: Any) extends Packet
object Response {
  def apply(requestId: Long, error: Any, result: Any) = new Response(PacketType.Response, requestId, error, result)
}

case class Notification(packetType: Int, method: String, args: List[Any]) extends Packet
object Notification{
  def apply(method: String, args: List[Any]) = new Notification(PacketType.Notification, method, args)
}

// An instance of ResponseHandler is given to the user when a request is received
// to allow the user to provide a response
case class ResponseHandler(writer: (Object) => Unit, requestId: Long) {
  def send(resp: Any*): Unit = send(resp.toList)
  def send(resp: List[Any]): Unit = {
    writer(Response(requestId, null, resp))
  }
}

// Provided by the user on session start to register any msgpack extended types
case class ExtendedType[A <: AnyRef](typeClass: Class[A], typeId: Byte,
                                     serializer: A => Array[Byte],
                                     deserializer: Array[Byte] => A)

object Session {
  def apply(in: InputStream, out: OutputStream, types: List[ExtendedType[_ <: AnyRef]] = List()): Session =
    new Impl(in, out, types)

  private final class Impl(in: InputStream, out: OutputStream, types: List[ExtendedType[_ <: AnyRef]])
    extends Session {

//    private val msgpack = new Msgpack(types)

    private var nextRequestId: Long = 1
    private val pendingRequests = mutable.Map.empty[Long, (ClassTag[_], Promise[Any])]

    private case class RequestEvent(method: String, args: List[Any], resp: ResponseHandler)
//    private val requestEvent = ReplaySubject[RequestEvent]

    private case class NotificationEvent(method: String, args: List[Any])
//    private val notificationEvent = ReplaySubject[NotificationEvent]

//    // Create a thread to listen for any packets
//    new Thread(new Runnable {
//      override def run(): Unit = {
//        try {
//          while (true) {
//            val packet = msgpack.readPacket(in)
//            parseMessage(packet)
//          }
//        } catch {
//          case e: JsonMappingException => // end-of-input
//        }
//      }
//    }).start()

//    def onRequest(callback: (String, List[Any], ResponseHandler) => Unit) =
//      requestEvent.subscribe( next => callback(next.method, next.args, next.resp) )
//
//    def onNotification(callback: (String, List[Any]) => Unit) =
//      notificationEvent.subscribe( next => callback(next.method, next.args) )

    trait DefaultsTo[Type, Default]
    object DefaultsTo {
      implicit def defaultDefaultsTo[T]: DefaultsTo[T, T] = null
      implicit def fallback[T, D]: DefaultsTo[T, D] = null
    }

//    def request[A <: Any : ClassTag](method: String, args: List[Any] = List())
//                                    (implicit default: A DefaultsTo Any): Future[A] = {
//      val ct = implicitly[ClassTag[A]]
//
//      val p = Promise[A]()
//
//      synchronized {
//        val id: Long = this.nextRequestId
//        this.nextRequestId += 1
//
//        this.pendingRequests += (id -> (ct, p.asInstanceOf[Promise[Any]]))
//        write(Request(id, method, args))
//      }
//
//      p.future
//    }

    override def notify(api: API): Unit = {
      // write(Notification(method, args))
      val bv = api.encode.getOrElse(sys.error("Could not encode packet"))
      out.write(bv.toByteArray)
      out.flush()
    }

//    private def write(obj: Object): Unit = {
//      msgpack.write(obj, out)
//    }

    private def parseMessage(packet: Packet) = packet match {
      case Request(_, id, method, args) =>
        ??? // this.requestEvent.onNext(RequestEvent(method, args, ResponseHandler(this.write, id)))

      case Response(_, id, err, result) =>
        synchronized {
          this.pendingRequests(id) match {
            case (tag, handler) =>
              if (err != null) handler.failure(new IllegalArgumentException(err.toString))
              else result match {
                case null => handler.success(null)
                case tag(x) => handler.success(x)
                case _ => handler.failure(new IllegalArgumentException("result type " + result.getClass + " is not expected type " + tag.runtimeClass))
              }
          }
          this.pendingRequests.remove(id)
        }

      case Notification(_, method, args) =>
        ??? // this.notificationEvent.onNext(NotificationEvent(method, args))
    }
  }
}
trait Session {
  def notify(api: API): Unit
}