/*
 * Neovim.scala
 * (Neovim UI Test)
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
import de.sciss.model.Model.Listener

import java.io.{InputStream, OutputStream}
import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.duration.Duration

object Neovim {
  def apply(in: InputStream, out: OutputStream): Neovim = {
    val session = Session(in, out)
    new Impl(session)
  }

  private final class Impl(session: Session) extends Neovim {
    override def quit(): Unit = this ! Command("qa!")

    override def eval(s: String): Future[String] = this !! Eval(s)

    override def ! (n: Notification): Unit = session.!(n)

    override def !! [A](req: Request[A], timeout: Duration): Future[A] = session.!!(req, timeout)

    override def addListener    (pf: Listener[Packet]): pf.type = session.addListener   (pf)
    override def removeListener (pf: Listener[Packet]): Unit    = session.removeListener(pf)
  }
}
trait Neovim extends Model[Packet] {
  def quit(): Unit

  def eval(s: String): Future[String]

  def ! (n: Notification): Unit

  def !! [A](req: Request[A], timeout: Duration = Duration(6, TimeUnit.SECONDS)): Future[A]
}
