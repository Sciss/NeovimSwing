package de.sciss.nvim

import java.io.{InputStream, OutputStream}

object Neovim {
  def apply(in: InputStream, out: OutputStream): Neovim = {
    val session = Session(in, out)
    new Impl(session)
  }

  private final class Impl(session: Session) extends Neovim {
    override def quit(): Unit = session.notify(Command("qa!"))
  }
}
trait Neovim {
  def quit(): Unit
}
