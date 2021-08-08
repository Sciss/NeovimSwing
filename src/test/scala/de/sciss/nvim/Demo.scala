/*
 * Demo.scala
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

import org.rogach.scallop.{ScallopConf, ScallopOption => Opt}

import java.io.File
import scala.swing.{MainFrame, Swing}

object Demo {
  def main(args: Array[String]): Unit = {
    object p extends ScallopConf(args) {
      import org.rogach.scallop._ // needed in Dotty
      printedName = "Neovim Swing Demo"
      private val defaultNv   = Neovim.Config()
      private val defaultView = View  .Config()

      val program: Opt[String] = opt(default = Some(defaultNv.program),
        descr = s"nvim executable (default: ${defaultNv.program})",
      )
      val cwd: Opt[File] = opt(default = Some(defaultNv.cwd),
        descr = "current working directory",
      )
      val open: Opt[File] = trailArg(required = false,
        descr = "file to open",
      )
      val rows: Opt[Int] = opt(default = Some(defaultView.rows),
        descr = s"number of rows in the editor (default: ${defaultView.rows})",
        validate = _ > 0
      )
      val columns: Opt[Int] = opt(default = Some(defaultView.columns),
        descr = s"number of columns in the editor (default: ${defaultView.columns})",
        validate = _ > 0
      )
      val fontFamily: Opt[String] = opt(default = Some(defaultView.fontFamily),
        descr = s"font family name  (default: ${defaultView.fontFamily})",
      )
      val fontSize: Opt[Float] = opt(default = Some(defaultView.fontSize),
        descr = s"font size  (default: ${defaultView.fontSize})",
        validate = _ > 0f,
      )
      val lineSpacing: Opt[Int] = opt(default = Some(defaultView.lineSpacing),
        descr = s"line spacing in percent (default: ${defaultView.lineSpacing})",
        validate = _ > 0,
      )
      val noInit: Opt[Boolean] = toggle(default = Some(!defaultNv.initVim),
        descrYes = "do not use standard init.vim",
      )
      val noSwap: Opt[Boolean] = toggle(default = Some(!defaultNv.swap),
        descrYes = "do not use swap file",
      )
      val readOnly: Opt[Boolean] = toggle(default = Some(defaultNv.readOnly),
        descrYes = "use read-only mode",
      )
      val notEditable: Opt[Boolean] = toggle(default = Some(!defaultNv.editable),
        descrYes = "do not allow text editing",
      )
      val notWritable: Opt[Boolean] = toggle(default = Some(!defaultNv.writable),
        descrYes = "do not allow text saving",
      )

      verify()
      val configNv: Neovim.Config = Neovim.Config(
        program   = program(),
        cwd       = cwd().getAbsoluteFile,
        open      = open.toOption,
        initVim   = !noInit(),
        swap      = !noSwap(),
        readOnly  = readOnly(),
        editable  = !notEditable(),
        writable  = !notWritable(),
      )
      val configView: View.Config = View.Config(
        rows        = rows(),
        columns     = columns(),
        fontFamily  = fontFamily(),
        fontSize    = fontSize(),
        lineSpacing = lineSpacing(),
      )
    }

    val res = run(p.configNv, p.configView)
    println(s"nivm exited with code $res")
    sys.exit(res)
  }

  def run(cNv: Neovim.Config, cView: View.Config): Int = {
    val nv = Neovim.start(cNv)
    Swing.onEDT {
      val v = View(nv, cView)
      new MainFrame {
        title = "nvim in Scala/Swing"
        contents = v.component
        pack().centerOnScreen()
        open()
        v.component.requestFocus()
//        cNv.open.foreach { fIn => nv ! Command(s"""e $fIn""") } // XXX TODO: escape path
      }
    }

    val res = nv.process.exitValue()
    res
  }
}
