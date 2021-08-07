package de.sciss.nvim

import java.io.{InputStream, OutputStream}
import scala.sys.process.{Process, ProcessIO}

object NivmTest {
  def main(args: Array[String]): Unit = run()

  def run(): Unit = {
    var is: InputStream   = null
    var os: OutputStream  = null

    val pb = Process(Seq("nvim", "-u", "NONE", "-n", "--embed"))
    val pio = new ProcessIO(
      out  => os = out,
      in   => is = in,
      _ => ()
    )
    val p = pb.run(pio)

    val n = Neovim(is, os)

    Thread.sleep(8000)

    n.quit()

    val res = p.exitValue()
    println(s"nivm exited with code $res")
  }
}
