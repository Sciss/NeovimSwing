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

import de.sciss.nvim.Neovim.Config
import org.rogach.scallop.{ScallopConf, ScallopOption => Opt}

import java.io.File
import scala.swing.{MainFrame, Swing}

object Demo {
  def main(args: Array[String]): Unit = {
    object p extends ScallopConf(args) {
      import org.rogach.scallop._ // needed in Dotty
      printedName = "Neovim Swing Demo"
      private val default = Config()

      val program: Opt[String] = opt(default = Some(default.program),
        descr = s"nvim executable (default: ${default.program})",
      )
      val cwd: Opt[File] = opt(default = Some(default.cwd),
        descr = "current working directory",
      )
      val open: Opt[File] = trailArg(required = false,
        descr = "file to open",
      )
      val rows: Opt[Int] = opt(default = Some(default.rows),
        descr = s"number of rows in the editor (default: ${default.rows})",
        validate = _ > 0
      )
      val columns: Opt[Int] = opt(default = Some(default.columns),
        descr = s"number of columns in the editor (default: ${default.columns})",
        validate = _ > 0
      )
      val noInit: Opt[Boolean] = toggle(default = Some(!default.initVim),
        descrYes = "do not use standard init.vim",
      )
      val noSwap: Opt[Boolean] = toggle(default = Some(!default.swap),
        descrYes = "do not use swap file",
      )
      val readOnly: Opt[Boolean] = toggle(default = Some(default.readOnly),
        descrYes = "use read-only mode",
      )
      val notEditable: Opt[Boolean] = toggle(default = Some(!default.editable),
        descrYes = "do not allow text editing",
      )
      val notWritable: Opt[Boolean] = toggle(default = Some(!default.writable),
        descrYes = "do not allow text saving",
      )

      verify()
      val config: Config = Config(
        program   = program(),
        cwd       = cwd().getAbsoluteFile,
        open      = open.toOption,
        rows      = rows(),
        columns   = columns(),
        initVim   = !noInit(),
        swap      = !noSwap(),
        readOnly  = readOnly(),
        editable  = !notEditable(),
        writable  = !notWritable(),
      )
    }

    val res = run(p.config)
    println(s"nivm exited with code $res")
    sys.exit(res)
  }

  def run(c: Config): Int = {
    val nv = Neovim.start(c)
    Swing.onEDT {
      val v = View(nv, rows = c.rows, columns = c.columns)
      new MainFrame {
        title = "nvim in Scala/Swing"
        contents = v.component
        pack().centerOnScreen()
        open()
        v.component.requestFocus()
        c.open.foreach { fIn => nv ! Command(s"""e $fIn""") } // XXX TODO: escape path
      }
    }

    val res = nv.process.exitValue()
    res
  }
}
