/*
 * Neovim.scala
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
import de.sciss.model.Model.Listener

import java.io.{File, InputStream, OutputStream}
import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.sys.process.{Process, ProcessIO}

object Neovim {
  case class Config(
                     program    : String        = "nvim",
                     cwd        : File          = new File(""),
                     open       : Option[File]  = None,
                     initVim    : Boolean       = true,
                     customInit : Option[File]  = None,
                     swap       : Boolean       = true,
                     readOnly   : Boolean       = false,
                     editable   : Boolean       = true,
                     writable   : Boolean       = true,
                   )

  def start(config: Config = Config()): Neovim.Started = {
    import config._
    var in  : InputStream   = null
    var out : OutputStream  = null

    val cmdB = Seq.newBuilder[String]
    cmdB += program
    cmdB += "--embed"
    val initPath = if (!initVim) Some("NONE") else customInit.map(_.getPath)
    initPath.foreach { path => cmdB ++= "-u" :: path :: Nil }
    if (!swap     ) cmdB += "-n"
    if (readOnly  ) cmdB += "-R"
    if (!editable ) cmdB += "-M"
    if (!writable ) cmdB += "-m"
    val cmd   = cmdB.result()
    // println(cmd)
    val pb    = Process(cmd, cwd = cwd)
    val pio = new ProcessIO(
      _out  => out = _out,
      _in   => in = _in,
      _ => ()
    )
    val p = pb.run(pio)
    val session = Session(in, out)
    val nv = new StartedImpl(p, session)
    open.foreach { fIn => nv ! Command(s"""e $fIn""") }
    nv
  }

  def connect(p: Option[Process], in: InputStream, out: OutputStream): Neovim = {
    val session = Session(in, out)
    new Impl(p, session)
  }

  private final class Impl(val processOption: Option[Process], protected val session: Session)
    extends Base

  private final class StartedImpl(val process: Process, protected val session: Session)
    extends Base with Started {

    override def processOption: Option[Process] = Some(process)
  }

  private abstract class Base extends Neovim {
    protected def session: Session

    override def quit(): Unit = this ! Command("qa!")

    override def eval(s: String): Future[String] = this !! Eval(s)

    override def ! (n: Notification): Unit = session.!(n)

    override def !! [A](req: Request[A], timeout: Duration): Future[A] = session.!!(req, timeout)

    override def addListener    (pf: Listener[Packet]): pf.type = session.addListener   (pf)
    override def removeListener (pf: Listener[Packet]): Unit    = session.removeListener(pf)
  }

  trait Started extends Neovim {
    def process: Process
  }
}
trait Neovim extends Model[Packet] {
  /** Corresponding shell process, if it was started from the JVM */
  def processOption: Option[Process]

  def quit(): Unit

  def eval(s: String): Future[String]

  /** Sends a notification to nvim without waiting for a response */
  def ! (n: Notification): Unit

  /** Sends a request to nvim, returning a future of the response */
  def !! [A](req: Request[A], timeout: Duration = Duration(6, TimeUnit.SECONDS)): Future[A]
}
