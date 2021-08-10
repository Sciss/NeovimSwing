/*
 * View.scala
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

import de.sciss.nvim.Redraw.{HighlightSet, ModeInfoSet, SetScrollRegion}

import java.awt.datatransfer.DataFlavor
import java.awt.event.{ComponentAdapter, ComponentEvent, FocusEvent, FocusListener, InputEvent, KeyAdapter, KeyEvent, MouseAdapter, MouseEvent}
import java.awt.image.BufferedImage
import java.awt.{Color, Cursor, RenderingHints, Toolkit}
import javax.swing.event.{AncestorEvent, AncestorListener}
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.swing.{Component, Dimension, Font, Graphics2D}

object View {
  case class Config(
                     rows         : Int     = 40,
                     columns      : Int     = 100,
                     fontFamily   : String  = Font.Monospaced,
                     fontSize     : Float   = 16f,
                     lineSpacing  : Float   = 1.05f,
                     initialFocus : Boolean = true,
                   ) {
    require (rows > 0 && columns > 0)
  }

  def apply(nv: Neovim, config: Config = Config()): View = {
    new Impl(nv, config)
  }

  private final class Impl(nv: Neovim, config: Config) extends Component with View {
    override def component: Component = this

    private[this] val DEBUG = false

    private var rows          = 0
    private var columns       = 0
    private val fnt           = Font(config.fontFamily, Font.Plain, 1).deriveFont(config.fontSize)
    private val fntBold       = fnt     .deriveFont(Font.Bold  .id)
    private val fntItalic     = fnt     .deriveFont(Font.Italic.id)
    private val fntBoldItalic = fntBold .deriveFont(Font.Italic.id)
    private val fm            = peer.getFontMetrics(fnt)
    private val cellWidth     = fm.getMaxAdvance
    private val blockHeight   = fm.getHeight
    private val cellHeight    = math.max(1, (blockHeight * config.lineSpacing + 0.5f).toInt)
    private val sync          = new AnyRef
    private var updates       = Vec.empty[Redraw.Update]
    private var scrollRegion   = SetScrollRegion(0, 0, 0, 0)
    private var modeName      = ""
    private var modeMap       = Map.empty[String, ModeInfoSet.Info]

    private var defaultFg : Color = null
    private var defaultBg : Color = Color.black   // need an init because of XOR mode
    private var defaultSp : Color = null
    private var colFg     : Color = null
    private var colBg     : Color = null
    private var colSp     : Color = null

    private var x = 0
    private var y = 0

    private var reverse   = false
    private var underline = false
    private var bold      = false
    private var italic    = false
    private var special   = false

    private var img : BufferedImage = null
    private var imgG: Graphics2D    = null

    private val DefaultTextCursor = TextCursor(TextCursor.Vertical, 25)
    private var textCursor        = DefaultTextCursor
    private var textCursorVisible = true

    private def updateFont(g: Graphics2D): Unit = g.setFont(
      if      (bold && italic)  fntBoldItalic
      else if (bold)            fntBold
      else if (italic)          fntItalic
      else                      fnt
    )

    private def resized(newRows: Int, newColumns: Int): Unit = {
      rows          = newRows
      columns       = newColumns
      val w         = cellWidth * columns
      val h         = cellHeight * rows
      initImage(w, h)
      preferredSize = new Dimension(w, h)
    }

    private var attached = false

    private def attach(): Unit = {
      if (!attached) {
        attached = true
        nv ! UI.Attach(width = columns, height = rows)
      }
    }

    private def detach(): Unit = {
      if (attached) {
        attached = false
        nv ! UI.Detach()
      }
    }

    def dispose(): Unit = {
      detach()
      disposeImage()
      sync.synchronized { updates = Vector.empty }
    }

    resized(newRows = config.rows, newColumns = config.columns)

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
            c match {
              case '<'  => "lt"
              case _    => s"_$c"
            }
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
            case KeyEvent.VK_INSERT   => "Insert"
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
          if (e.isShiftDown && isSpecial && name2 != "lt") sb.append("S-")  // XXX TODO ugly special casing
          if (e.isControlDown ) sb.append("C-")
          if (e.isMetaDown    ) sb.append("M-")
          if (e.isAltDown     ) sb.append("A-")
          sb.append(name2)
          sb.append('>')
          sb.toString
        }
        if (DEBUG) println(s"TYPED: $name")
        // XXX TODO dirty hack to have some form of clipboard paste
        if (name != "<C-V>") {
          nv ! Input(name)
        } else {
          val cb      = Toolkit.getDefaultToolkit.getSystemClipboard
          val fl      = DataFlavor.stringFlavor
          val hasClip = cb.isDataFlavorAvailable(fl)
          if (hasClip) {
            val data = cb.getData(fl).toString
            nv ! Paste(data)
          }
        }
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
          // println(p)
          nv ! p
        }
      }

//      override def componentHidden(e: ComponentEvent): Unit =
//        disposeImage()
    })

    peer.addAncestorListener(new AncestorListener {
      // private var first = true

      def ancestorAdded(e: AncestorEvent): Unit = {
        if (config.initialFocus) requestFocusInWindow()
        attach()
      }

      def ancestorMoved   (e: AncestorEvent): Unit = ()
      def ancestorRemoved (e: AncestorEvent): Unit = disposeImage()
    })

    peer.addFocusListener(new FocusListener {
      private def check(): Unit =
        if (textCursor.shape == TextCursor.Block) {
          peer.repaint(x, y, cellWidth, cellHeight)
        }

      override def focusGained(e: FocusEvent): Unit = check()
      override def focusLost  (e: FocusEvent): Unit = check()
    })

    opaque = true

//    private val HiddenCursor: java.awt.Cursor =
//      java.awt.Toolkit.getDefaultToolkit.createCustomCursor(
//        new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB), new java.awt.Point(0, 0), "hidden")

    override protected def paintComponent(g: Graphics2D): Unit = {
      super.paintComponent(g)
      g.setBackground(defaultBg)
      g.clearRect(0, 0, peer.getWidth, peer.getHeight)
      paintUpdates()
      /*if (img != null)*/ g.drawImage(img, 0, 0, null)
      if (textCursorVisible) {
        g.setColor(defaultFg) // XXX TODO: should use inverted colors of current cell
        textCursor.shape match {
          case TextCursor.Block =>
            if (hasFocus) {
              g.setXORMode(defaultBg)
              g.fillRect(x, y, cellWidth, blockHeight)
            } else {
              g.drawRect(x, y, cellWidth - 1, blockHeight - 1)
            }
          case TextCursor.Horizontal =>
            val e = 2 // textCursor.cellExtent(cellHeight)
            g.fillRect(x, y + blockHeight - e, cellWidth, e)
          case TextCursor.Vertical =>
            val e = 2 // defaults look bad: textCursor.cellExtent(cellHeight)
            g.fillRect(x, y, e, blockHeight)
        }
      }
    }

    private def initImage(width: Int, height: Int): Unit = {
//      println(s"initImage($width, $height)")
//      (new Exception).printStackTrace()
      val imgNew = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
      val g = imgNew.createGraphics()
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g.setColor      (colFg)
      g.setBackground (colBg)
      updateFont(g)
      g.clearRect(0, 0, width, height)
      if (img != null) {
        g.drawImage(img, 0, 0, null)
      }
      disposeImage()
      img   = imgNew
      imgG  = g
      // println(s"new g is ${g.hashCode().toHexString}")
    }

    private def disposeImage(): Unit = {
      if (imgG != null) {
//        imgG.dispose()
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

      var g = imgG
      // println(s"${u.size} updates for ${g.hashCode().toHexString}")
      // g.setFont(fnt)
      val fm = g.getFontMetrics

      u.foreach {
        case Redraw.CursorGoto(row, col) =>
          x = col * cellWidth
          y = row * cellHeight

        case Redraw.Put(text) =>
          // println(s"Put(${text})")
          val w   = text.length * cellWidth
          val col = g.getColor
          val ya  = y + fm.getAscent
          if (reverse) {
            g.fillRect(x, y, w, cellHeight)
            g.setColor(g.getBackground)
          } else {
            g.clearRect(x, y, w, cellHeight)
          }
          g.drawString(text, x, ya)
          if (underline) {
            g.drawLine(x, ya + 1, x + w - 1, ya + 1)
          }
          if (reverse) g.setColor(col)
          x += w

        case Redraw.HighlightSet(attr) =>
          colFg     = defaultFg
          colBg     = defaultBg
          colSp     = defaultSp
          underline = false
          bold      = false
          italic    = false
          reverse   = false
          special   = false
          g.setBackground (colBg)
          g.setColor      (colFg)
          updateFont(g)
          attr.foreach {
            case HighlightSet.Bold() =>
              bold = true
              updateFont(g)

            case HighlightSet.Italic() =>
              italic = true
              updateFont(g)

            case HighlightSet.Foreground(c) =>
              val col = new Color(c)
              colFg = col
              g.setColor(col)

            case HighlightSet.Background(c) =>
              val col = new Color(c)
              colBg = col
              g.setBackground(col)

            case HighlightSet.Reverse() =>
              reverse = true
//              val bg = g.getBackground
//              val fg = g.getColor
//              g.setBackground(fg)
//              g.setColor(bg)

            case HighlightSet.Underline() =>
              underline = true

            case HighlightSet.Special(c) =>
              val col = new Color(c)
              colSp     = col
              special   = true
              g.setColor(col)

            case other =>
              println(s"IGNORE ATTR $other")
          }

        case Redraw.OptionSet(_, _) =>

        case Redraw.HlGroupSet(_, _) =>

        case ui @ Redraw.ModeInfoSet(_, _) =>
          modeMap = ui.asMap

        case /*ui @*/ Redraw.ModeChange(name, _) =>
          if (modeName != name) {
            modeName = name
            modeMap.get(name) match {
              case Some(info) =>
                textCursor = info.cursor.getOrElse(DefaultTextCursor)
              case _ =>
            }
          }

        case Redraw.DefaultColorsSet(rgbFg, rgbBg, rgbSp, _, _) =>
          defaultFg = new Color(rgbFg)
          defaultBg = new Color(rgbBg)
          defaultSp = new Color(rgbSp)

        case Redraw.Flush() =>

        case Redraw.MouseOn() =>
          cursor = null

        case Redraw.MouseOff() =>
          cursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR) // HiddenCursor

        case Redraw.BusyStart() =>
          textCursorVisible = false

        case Redraw.BusyStop() =>
          textCursorVisible = true

        case Redraw.Clear() =>
          // if (reverse) {
          //   g.fillRect (0, 0, peer.getWidth, peer.getHeight)
          // } else {
            g.clearRect(0, 0, peer.getWidth, peer.getHeight)
          // }

        case Redraw.EolClear() =>
          // if (reverse) {
          //   g.fillRect (x, y, peer.getWidth - x, cellHeight)
          // } else {
            g.clearRect(x, y, peer.getWidth - x, cellHeight)
          // }

        case /*ui @*/ Redraw.Resize(newColumns, newRows) =>
//          println(ui)
          if (newColumns != columns || newRows != rows) {
            resized(newRows = newRows, newColumns = newColumns)
            g = imgG  // switch to new context
          }

        case /*ui @*/ Redraw.WinViewport(_, _, _, _, _, _) =>
          // println(ui)

        case Redraw.UpdateFg(c) =>
          val col = new Color(c)
          colFg = col
          // defaultFg = col
          g.setColor(col)

        case Redraw.UpdateBg(c) =>
          val col = new Color(c)
          colBg = col
          // defaultBg = col
          g.setBackground(col)

        case Redraw.UpdateSp(_) =>

        case sr: Redraw.SetScrollRegion =>
          scrollRegion = sr

        case Redraw.Scroll(count) =>
          val sr = scrollRegion
          // println(s"scroll $count in $sr")
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
      case Redraw(u) if attached =>
        // println(s"${u.size} updates")
        sync.synchronized {
          updates ++= u
        }
//        Swing.onEDT {
//          paintUpdates()
//        }
        repaint()
    }

    attach()
  }
}
trait View {
  def component: Component

  def dispose(): Unit
}
