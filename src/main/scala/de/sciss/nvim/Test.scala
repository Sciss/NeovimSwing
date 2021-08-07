/*
 * Test.scala
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

import java.io.{InputStream, OutputStream}
import scala.concurrent.ExecutionContext.Implicits._
import scala.swing.{MainFrame, Swing}
import scala.sys.process.{Process, ProcessIO}
import scala.util.{Failure, Success}

object Test {
  def main(args: Array[String]): Unit = run()

  def run(): Unit = {
    var is: InputStream   = null
    var os: OutputStream  = null

    val pb = Process(Seq("nvim", /*"-u", "NONE",*/ "-n", "--embed"))
    val pio = new ProcessIO(
      out  => os = out,
      in   => is = in,
      _ => ()
    )
    val p = pb.run(pio)

    val nv = Neovim(is, os)

//    Thread.sleep(8000)
    val expr = "12 + 34"
//    val expr = "12 + BLA"
    nv.eval(expr).onComplete {
      case Success(s) =>
        println(s"nvim says $expr equals $s")
//        n.quit()
        // nv ! Put("Hello world." :: Nil, "c", after = false, follow = true)
        Swing.onEDT {
          val v = View(nv)
          new MainFrame {
            title = "nvim in Scala/Swing"
            contents = v.component
            pack().centerOnScreen()
            open()
            v.component.requestFocus()
          }
        }

      case Failure(e) =>
        e.printStackTrace()
    }

    val res = p.exitValue()
    println(s"nivm exited with code $res")
  }
}
