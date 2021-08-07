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

import de.sciss.nvim.UI.Redraw.{HighlightSet, SetScrollRegion}

import java.awt.event.{ComponentAdapter, ComponentEvent, InputEvent, KeyAdapter, KeyEvent, MouseAdapter, MouseEvent}
import java.awt.image.BufferedImage
import java.awt.{Cursor, RenderingHints}
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.swing.{Color, Component, Dimension, Font, Graphics2D}

object View {
  def apply(nv: Neovim, rows: Int = 24, columns: Int = 60): View = {
    require (rows > 0 && columns > 0)
    new Impl(nv, rows0 = rows, columns0 = columns)
  }

  private final class Impl(nv: Neovim, rows0: Int, columns0: Int) extends Component with View {
    override def component: Component = this

    private[this] val DEBUG = false

    private var rows        = rows0
    private var columns     = columns0
    private val fnt         = Font(Font.Monospaced, Font.Plain, 14)
    private val fntB        = fnt.deriveFont(Font.Bold.id)
    private val fm          = peer.getFontMetrics(fnt)
    private val cellWidth   = fm.getMaxAdvance
    private val cellHeight  = fm.getHeight
    private val sync        = new AnyRef
    private var updates     = Vec.empty[UI.Redraw.Update]
    private var scrollRegion = SetScrollRegion(0, 0, 0, 0)

    private def resized(newRows: Int, newColumns: Int): Unit = {
      rows          = newRows
      columns       = newColumns
      val w         = cellWidth * columns
      val h         = cellHeight * rows
      initImage(w, h)
      preferredSize = new Dimension(w, h)
    }

    resized(rows0, columns0)

    peer.addKeyListener(new KeyAdapter {
//      private var consumed = false

      override def keyPressed(e: KeyEvent): Unit = {
        val mod     = e.getModifiersEx
        val hasMod0 = (mod & (InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK)) != 0
        // val hasMod  = hasMod0 || e.isShiftDown
        // https://neovim.io/doc/user/intro.html#keycodes
        val name0 = e.getKeyCode match {
          case KeyEvent.VK_ENTER      => "Enter"
          case KeyEvent.VK_BACK_SPACE => "BS"
          case KeyEvent.VK_TAB        => "Tab"
          case KeyEvent.VK_ESCAPE     => "Esc"
          case KeyEvent.VK_SPACE      => "Space"
          case KeyEvent.VK_PAGE_UP    => "PageUp"
          case KeyEvent.VK_PAGE_DOWN  => "PageDown"
          case KeyEvent.VK_END        => "End"
          case KeyEvent.VK_HOME       => "Home"
          case KeyEvent.VK_LEFT       => "Left"
          case KeyEvent.VK_UP         => "Up"
          case KeyEvent.VK_RIGHT      => "Right"
          case KeyEvent.VK_DOWN       => "Down"
          case KeyEvent.VK_LESS       => "Down"
          case KeyEvent.VK_DELETE     => "Del"
          case KeyEvent.VK_F1         => "F1"
          case KeyEvent.VK_F2         => "F2"
          case KeyEvent.VK_F3         => "F3"
          case KeyEvent.VK_F4         => "F4"
          case KeyEvent.VK_F5         => "F5"
          case KeyEvent.VK_F6         => "F6"
          case KeyEvent.VK_F7         => "F7"
          case KeyEvent.VK_F8         => "F8"
          case KeyEvent.VK_F9         => "F9"
          case KeyEvent.VK_F10        => "F10"
          case KeyEvent.VK_F11        => "F11"
          case KeyEvent.VK_F12        => "F12"
          case KeyEvent.VK_HELP       => "Help"
          case KeyEvent.VK_UNDO       => "Undo"
          case KeyEvent.VK_INSERT     => "Insert"
          case KeyEvent.VK_SHIFT      => ""
          case KeyEvent.VK_CONTROL    => ""
          case KeyEvent.VK_ALT        => ""
          case KeyEvent.VK_ALT_GRAPH  => ""
          case KeyEvent.VK_META       => ""
          case KeyEvent.VK_UNDEFINED  => ""
          case other                  =>
            val c = if (hasMod0) other.toChar else e.getKeyChar
            s"_$c"
        }
        if (name0.isEmpty) return

        val isKP    = e.getKeyLocation == KeyEvent.KEY_LOCATION_NUMPAD
        val name1 = if (!isKP) name0 else {
          val base = e.getKeyCode match {
            case KeyEvent.VK_KP_UP    => "Up"
            case KeyEvent.VK_KP_DOWN  => "Down"
            case KeyEvent.VK_KP_LEFT  => "Left"
            case KeyEvent.VK_KP_RIGHT => "Right"
            case KeyEvent.VK_PLUS     => "Plus"
            case KeyEvent.VK_MINUS    => "Minus"
            case KeyEvent.VK_PERIOD   => "Point"
            case KeyEvent.VK_COMMA    => "Comma"
            case KeyEvent.VK_EQUALS   => "Equal"
            case other                =>
              val c = if (hasMod0) other.toChar else e.getKeyChar
              c.toString
          }
          s"k$base"
        }
        val isSpecial = name1.charAt(0) != '_'
        val name2     = if (isSpecial) name1 else name1.substring(1)
        val name      = if (!hasMod0 && !isKP && !isSpecial) name2 else {
          val sb = new java.lang.StringBuilder
          sb.append('<')
          if (e.isShiftDown & isSpecial) sb.append("S-")
          if (e.isControlDown ) sb.append("C-")
          if (e.isMetaDown    ) sb.append("M-")
          if (e.isAltDown     ) sb.append("A-")
          sb.append(name2)
          sb.append('>')
          sb.toString
        }
        if (DEBUG) println(s"TYPED: $name")
        nv ! Input(name :: Nil)
      }
    })

    peer.addMouseListener(new MouseAdapter {
      override def mousePressed(e: MouseEvent): Unit =
        requestFocus()
    })

    peer.addComponentListener(new ComponentAdapter {
      override def componentResized(e: ComponentEvent): Unit = {
        val c = e.getComponent
        val newColumns  = c.getWidth  / cellWidth
        val newRows     = c.getHeight / cellHeight
        if (newColumns != columns || newRows != rows) {
          val p = UI.TryResize(width = newColumns, height = newRows)
          println(p)
          nv ! p
        }
      }

      override def componentHidden(e: ComponentEvent): Unit =
        disposeImage()
    })

    opaque = true

    private lazy val HiddenCursor: java.awt.Cursor =
      java.awt.Toolkit.getDefaultToolkit.createCustomCursor(
        new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB), new java.awt.Point(0, 0), "hidden")

    private var defaultFg : Color = null
    private var defaultBg : Color = null
    private var fg        : Color = null
    private var bg        : Color = null

    private var x = 0
    private var y = 0

    private var img : BufferedImage = null
    private var imgG: Graphics2D    = null

    override protected def paintComponent(g: Graphics2D): Unit = {
      super.paintComponent(g)
      g.setBackground(defaultBg)
      g.clearRect(0, 0, peer.getWidth, peer.getHeight)
      paintUpdates()
      /*if (img != null)*/ g.drawImage(img, 0, 0, null)
    }

    private def initImage(width: Int, height: Int): Unit = {
//      println(s"initImage($width, $height)")
//      (new Exception).printStackTrace()
      val imgNew = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
      val g = imgNew.createGraphics()
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g.setColor      (fg)
      g.setBackground (bg)
      g.clearRect(0, 0, width, height)
      if (img != null) g.drawImage(img, 0, 0, null)
      disposeImage()
      img   = imgNew
      imgG  = g
    }

    private def disposeImage(): Unit = {
      if (imgG != null) {
        imgG.dispose()
        imgG = null
        img  = null
//        println(s"disposeImage()")
//        (new Exception).printStackTrace()
      }
    }

    private def paintUpdates(): Unit = {
//      require (EventQueue.isDispatchThread)
//      super.paintComponent(g)
      if (imgG == null) initImage(peer.getWidth, peer.getHeight)

      val u = sync.synchronized {
        val res = updates
        updates = Vector.empty
        res
      }
      if (u.isEmpty) return

      val g = imgG
      g.setFont(fnt)
      val fm = g.getFontMetrics
      var reverse = false
      u.foreach {
        case UI.Redraw.CursorGoto(row, col) =>
          x = col * cellWidth
          y = row * cellHeight

        case UI.Redraw.Put(text) =>
          val w = text.length * cellWidth
          if (reverse) {
            val col = g.getColor
            g.fillRect(x, y, w, cellHeight)
            g.setColor(g.getBackground)
            g.drawString(text, x, y + fm.getAscent)
            g.setColor(col)
          } else {
            g.clearRect(x, y, w, cellHeight)
            g.drawString(text, x, y + fm.getAscent)
          }
          x += w

        case UI.Redraw.HighlightSet(attr) =>
          g.setFont(fnt)
          fg = defaultFg
          bg = defaultBg
          g.setBackground(bg)
          g.setColor(fg)
          reverse = false
          attr.foreach {
            case HighlightSet.Bold() =>
              g.setFont(fntB)

            case HighlightSet.Foreground(c) =>
              val col = new Color(c)
              fg = col
              g.setColor(col)

            case HighlightSet.Background(c) =>
              val col = new Color(c)
              bg = col
              g.setBackground(col)

            case HighlightSet.Reverse() =>
              reverse = true
//              val bg = g.getBackground
//              val fg = g.getColor
//              g.setBackground(fg)
//              g.setColor(bg)

            case other =>
              println(s"IGNORE ATTR $other")
          }

        case UI.Redraw.OptionSet(_, _) =>

        case UI.Redraw.HlGroupSet(_, _) =>

        case UI.Redraw.ModeInfoSet(_, _) =>

        case UI.Redraw.ModeChange(_, _) =>

        case UI.Redraw.DefaultColorsSet(rgbFg, rgbBg, _ /*rgbSp*/, _, _) =>
          defaultFg = new Color(rgbFg)
          defaultBg = new Color(rgbBg)

        case UI.Redraw.Flush() =>

        case UI.Redraw.MouseOn() =>
          cursor = null

        case UI.Redraw.MouseOff() =>
          cursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR) // HiddenCursor

        case UI.Redraw.Clear() =>
          // if (reverse) {
          //   g.fillRect (0, 0, peer.getWidth, peer.getHeight)
          // } else {
            g.clearRect(0, 0, peer.getWidth, peer.getHeight)
          // }

        case UI.Redraw.EolClear() =>
          // if (reverse) {
          //   g.fillRect (x, y, peer.getWidth - x, cellHeight)
          // } else {
            g.clearRect(x, y, peer.getWidth - x, cellHeight)
          // }

        case ui @ UI.Redraw.Resize(newColumns, newRows) =>
          println(ui)
          if (newColumns != columns || newRows != rows) {
            resized(newRows = newRows, newColumns = newColumns)
          }

        case UI.Redraw.WinViewport(grid: Int, _ /*win*/, topLine, botLine, curLine, curCol) =>

        case UI.Redraw.UpdateFg(c) =>
          val col = new Color(c)
          fg = col
          // defaultFg = col
          g.setColor(col)

        case UI.Redraw.UpdateBg(c) =>
          val col = new Color(c)
          bg = col
          // defaultBg = col
          g.setBackground(col)

        case UI.Redraw.UpdateSp(_) =>

        case sr: UI.Redraw.SetScrollRegion =>
          scrollRegion = sr

        case UI.Redraw.Scroll(count) =>
          val sr = scrollRegion
          g.copyArea(
            sr.left * cellWidth,
            (sr.top + count)  * cellHeight,
            ((sr.right - sr.left) + 1) * cellWidth,
            ((sr.bot   - sr.top ) + 1) * cellHeight,
            0, -count * cellHeight
          )

        case other =>
          println(s"IGNORE $other")
      }
    }

    nv.addListener {
      case UI.Redraw(u) =>
        sync.synchronized {
          updates ++= u
        }
//        Swing.onEDT {
//          paintUpdates()
//        }
        repaint()
    }
    nv ! UI.Attach(width = columns, height = rows)
  }
}
trait View {
  def component: Component
}
