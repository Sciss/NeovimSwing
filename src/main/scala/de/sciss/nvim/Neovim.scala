package de.sciss.nvim

import java.io.{InputStream, OutputStream}
import scala.concurrent.Future

object Neovim {
  def apply(in: InputStream, out: OutputStream): Neovim = {
    val session = Session(in, out)
    new Impl(session)
  }

  private final class Impl(session: Session) extends Neovim {
    override def quit(): Unit = session.notify(Command("qa!"))

    override def eval(s: String): Future[String] = session.request(Eval(s))
  }
}
trait Neovim {
  def quit(): Unit

  def eval(s: String): Future[String]
}
