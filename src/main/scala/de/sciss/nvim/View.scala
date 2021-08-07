/*
 * View.scala
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

import de.sciss.nvim.UI.Redraw.HighlightSet

import java.awt.RenderingHints
import scala.swing.{Component, Dimension, Font, Graphics2D}
import scala.collection.immutable.{IndexedSeq => Vec}

object View {
  def apply(nv: Neovim, rows: Int = 24, columns: Int = 60): View = {
    require (rows > 0 && columns > 0)
    new Impl(nv, rows = rows, columns = columns)
  }

  private final class Impl(nv: Neovim, rows: Int, columns: Int) extends Component with View {
    override def component: Component = this

    private val fnt         = Font(Font.Monospaced, Font.Plain, 14)
    private val fntB        = fnt.deriveFont(Font.Bold.id)
    private val fm          = peer.getFontMetrics(fnt)
    private val cellWidth   = fm.getMaxAdvance
    private val cellHeight  = fm.getHeight
    private val sync        = new AnyRef
    private var updates     = Vec.empty[UI.Redraw.Update]

    preferredSize = new Dimension(cellWidth * columns, cellHeight * rows)

    override protected def paintComponent(g: Graphics2D): Unit = {
      super.paintComponent(g)

      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

      val u = sync.synchronized {
        val res = updates
        updates = Vector.empty
        res
      }
      g.setFont(fnt)
      val fm = g.getFontMetrics
      var x = 0
      var y = 0
      u.foreach {
        case UI.Redraw.CursorGoto(row, col) =>
          x = col * cellWidth
          y = row * cellHeight + fm.getAscent

        case UI.Redraw.Put(text) =>
          g.drawString(text, x, y)
          x += text.length * cellWidth

        case UI.Redraw.HighlightSet(attr) =>
          g.setFont(fnt)
          attr.foreach {
            case HighlightSet.Bold() =>
              g.setFont(fntB)
            case _ /*other*/ =>
//              println(s"IGNORE $other")
          }

        case _ =>
      }
    }

    nv.addListener {
      case UI.Redraw(u) =>
        sync.synchronized {
          updates ++= u
        }
        repaint()
    }
    nv ! UI.Attach(width = columns, height = rows)
  }
}
trait View {
  def component: Component
}
