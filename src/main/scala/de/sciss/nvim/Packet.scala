/* {{{
 * Packet.scala
 * (NeovimSwing)
 *
 * Copyright (c) 2021 Hanns Holger Rutz. All rights reserved.
 *
 * This software is published under the GNU Lesser General Public License v2.1+
 *
 *
 * For further information, please contact Hanns Holger Rutz at
 * contact@sciss.de }}}
 */

package de.sciss.nvim

import wvlet.airframe.msgpack.spi.Value.{ArrayValue, BooleanValue, LongValue, MapValue, StringValue}
import wvlet.airframe.msgpack.spi.{Value, ValueFactory}

import scala.collection.immutable.{IndexedSeq => Vec, Seq => ISeq}

object Packet {
  final val RequestType       = 0
  final val ResponseType      = 1
  final val NotificationType  = 2
}
sealed trait Packet

sealed trait NotificationOrRequest extends Packet {
  def method: String
  def params: ArrayValue
}

trait NotificationFactory {
  def method: String

  def decode(params: Value): Notification
}

object Notification {
  private val factories = Map(
    Redraw.method -> Redraw
  )

  def get(method: String): Option[NotificationFactory] = factories.get(method)
}
sealed trait Notification extends NotificationOrRequest

sealed trait Request[A] extends NotificationOrRequest {
  def decode(v: Value): A
}

final case class Command(s: String) extends Notification {
  def method: String = "nvim_command"

  override def params: ArrayValue =
    ArrayValue(StringValue(s) +: Vector.empty)
}

final case class Eval(s: String) extends Request[String] {
  override def method: String = "nvim_eval"

  override def params: ArrayValue =
    ArrayValue(StringValue(s) +: Vector.empty)

  override def decode(v: Value): String = v match {
    case StringValue(s) => s
    case other          => other.toString
  }
}

final case class Put(lines: ISeq[String], tpe: String, after: Boolean, follow: Boolean) extends Notification {
  def method: String = "nvim_put"

  override def params: ArrayValue =
    ArrayValue(ArrayValue(lines.iterator.map(StringValue.apply).toIndexedSeq) +: StringValue(tpe) +:
      BooleanValue(after) +: BooleanValue(follow) +: Vector.empty)
}

final case class Input(keys: String) extends Notification {
  def method: String = "nvim_input"

  override def params: ArrayValue =
    ArrayValue(StringValue(keys) +: Vector.empty)
}

final case class Paste(data: String, crLf: Boolean = true, phase: Int = -1) extends Notification {
  def method: String = "nvim_paste"

  override def params: ArrayValue =
    ArrayValue(StringValue(data) +: BooleanValue(crLf) +:  LongValue(phase) +: Vector.empty)
}

object UI {
  sealed trait Option {
    def key   : String
    def value : Value
  }

  case class Attach(width: Int, height: Int, options: Set[Option] = Set.empty) extends Notification {
    override def method: String = "nvim_ui_attach"

    override def params: ArrayValue = {
      val optionsV = options.iterator.map { opt => (StringValue(opt.key), opt.value) } .toMap[Value, Value]
      ArrayValue(
        ValueFactory.newInteger(width) +:
        ValueFactory.newInteger(height) +: MapValue(optionsV) +: Vector.empty
      )
    }
  }

  case class Detach() extends Notification {
    override def method: String = "nvim_ui_detach"

    override def params: ArrayValue = ArrayValue(Vector.empty)
  }

  case class TryResize(width: Int, height: Int) extends Notification {
    override def method: String = "nvim_ui_try_resize"

    override def params: ArrayValue = {
      ArrayValue(
        ValueFactory.newInteger(width) +: ValueFactory.newInteger(height) +: Vector.empty
      )
    }
  }
}

object Redraw extends NotificationFactory {
  final val method = "redraw"

  private val updateFactories: Map[String, UpdateFactory] = Set(
    OptionSet,
    DefaultColorsSet,
    UpdateFg,
    UpdateBg,
    UpdateSp,
    HlGroupSet,
    Resize,
    Clear,
    WinViewport,
    CursorGoto,
    EolClear,
    HighlightSet,
    Put,
    ModeInfoSet,
    ModeChange,
    MouseOn,
    MouseOff,
    SetScrollRegion,
    Scroll,
    BusyStart,
    BusyStop,
    Flush,
    SetTitle,
  ) .iterator.map { f => (f.name, f) } .toMap

  def getUpdate(name: String): scala.Option[UpdateFactory] = updateFactories.get(name)

  override def decode(params: Value): Notification = params match {
    case ArrayValue(updatesV) =>
      val updatesB = Vector.newBuilder[Update]
      updatesV.foreach {
        case ArrayValue(StringValue(name) +: tuples) =>
          val updateF = getUpdate(name).getOrElse(sys.error(s"Unknown update $name"))
          tuples.foreach { case ArrayValue(updateV) =>
            val u = updateF.decode(updateV)
            updatesB += u
          }

        //          case ArrayValue(other) =>
        //            println(s"SIZE ${other.size}")
        //            sys.error(other.getClass.toString + "; " + other.toString)

        case other => sys.error(other.getClass.toString + "; " + other.toString)
      }
      val updates = updatesB.result()
      Redraw(updates)

    case other => sys.error(other.toString)
  }

  trait UpdateFactory {
    def name: String

    def decode(v: Seq[Value]): Update
  }

  sealed trait Update {
    def name  : String
    def param : Value
  }

  object SetTitle extends UpdateFactory {
    final val name = "set_title"

    override def decode(v: Seq[Value]): Update = v match {
      case Seq(StringValue(s)) => SetTitle(s)
      case other => sys.error(other.toString)
    }
  }
  case class SetTitle(s: String) extends Update {
    override def name: String = SetTitle.name

    override def param: Value = StringValue(s)
  }

  object OptionSet extends UpdateFactory {
    final val name = "option_set"

    override def decode(v: Seq[Value]): Update = v match {
      case Seq(StringValue(key), value) => OptionSet(key, value)
      case other => sys.error(other.toString)
    }
  }
  case class OptionSet(key: String, value: Value) extends Update {
    override def name: String = OptionSet.name

    override def param: Value = ArrayValue(StringValue(key) +: value +: Vector.empty)
  }

  object DefaultColorsSet extends UpdateFactory {
    final val name = "default_colors_set"

    override def decode(v: Seq[Value]): Update = v match {
      // cf. https://github.com/wvlet/airframe/issues/1774
      case Seq(LongValue(rgbFg), LongValue(rgbBg), LongValue(rgbSp), LongValue(termFg), LongValue(termBg)) =>
        DefaultColorsSet(rgbFg = rgbFg.toInt, rgbBg = rgbBg.toInt, rgbSp = rgbSp.toInt, termFg = termFg.toInt, termBg = termBg.toInt)

      case other => sys.error(other.toString)
    }
  }
  case class DefaultColorsSet(rgbFg: Int, rgbBg: Int, rgbSp: Int, termFg: Int, termBg: Int) extends Update {
    override def name: String = DefaultColorsSet.name

    override def param: Value = ArrayValue(
      Vector(
        ValueFactory.newInteger(rgbFg),
        ValueFactory.newInteger(rgbBg),
        ValueFactory.newInteger(rgbSp),
        ValueFactory.newInteger(termFg),
        ValueFactory.newInteger(termBg),
      )
    )
  }

  object UpdateFg extends UpdateFactory {
    final val name = "update_fg"

    override def decode(v: Seq[Value]): Update = v match {
      case Seq(LongValue(color)) => UpdateFg(color = color.toInt)
      case other => sys.error(other.toString)
    }
  }
  case class UpdateFg(color: Int) extends Update {
    override def name: String = UpdateFg.name

    override def param: Value = ValueFactory.newInteger(color)
  }

  object UpdateBg extends UpdateFactory {
    final val name = "update_bg"

    override def decode(v: Seq[Value]): Update = v match {
      case Seq(LongValue(color)) => UpdateBg(color = color.toInt)
      case other => sys.error(other.toString)
    }
  }
  case class UpdateBg(color: Int) extends Update {
    override def name: String = UpdateBg.name

    override def param: Value = ValueFactory.newInteger(color)
  }

  object UpdateSp extends UpdateFactory {
    final val name = "update_sp"

    override def decode(v: Seq[Value]): Update = v match {
      case Seq(LongValue(color)) => UpdateSp(color = color.toInt)
      case other => sys.error(other.toString)
    }
  }
  case class UpdateSp(color: Int) extends Update {
    override def name: String = UpdateSp.name

    override def param: Value = ValueFactory.newInteger(color)
  }

  object HlGroupSet extends UpdateFactory {
    final val name = "hl_group_set"

    override def decode(v: Seq[Value]): Update = v match {
      case Seq(StringValue(group), LongValue(hlId)) => HlGroupSet(group = group, hlId = hlId.toInt)
      case other => sys.error(other.toString)
    }
  }
  case class HlGroupSet(group: String, hlId: Int) extends Update {
    override def name: String = HlGroupSet.name

    override def param: Value = ArrayValue(StringValue(group) +: ValueFactory.newInteger(hlId) +: Vector.empty)
  }

  object Resize extends UpdateFactory {
    final val name = "resize"

    override def decode(v: Seq[Value]): Update = v match {
      case Seq(LongValue(width), LongValue(height)) => Resize(width = width.toInt, height = height.toInt)
      case other => sys.error(other.toString)
    }
  }
  case class Resize(width: Int, height: Int) extends Update {
    override def name: String = Resize.name

    override def param: Value = ArrayValue(ValueFactory.newInteger(width) +: ValueFactory.newInteger(height) +: Vector.empty)
  }

  object Clear extends UpdateFactory {
    final val name = "clear"

    override def decode(v: Seq[Value]): Update = v match {
      case Seq() => Clear()
      case other => sys.error(other.toString)
    }
  }
  case class Clear() extends Update {
    override def name: String = Clear.name

    override def param: Value = ValueFactory.newNil
  }

  object WinViewport extends UpdateFactory {
    final val name = "win_viewport"

    override def decode(v: Seq[Value]): Update = v match {
      // cf. https://github.com/wvlet/airframe/issues/1774
      case Seq(LongValue(grid), winV, LongValue(topLine), LongValue(botLine), LongValue(curLine), LongValue(curCol)) =>
        WinViewport(grid = grid.toInt, win = winV, topLine = topLine.toInt, botLine = botLine.toInt,
          curLine = curLine.toInt, curCol = curCol.toInt)

      case other => sys.error(other.toString)
    }
  }
  case class WinViewport(grid: Int, win: Value, topLine: Int, botLine: Int, curLine: Int, curCol: Int) extends Update {
    override def name: String = WinViewport.name

    override def param: Value = ArrayValue(
      Vector(
        ValueFactory.newInteger(grid),
        win,
        ValueFactory.newInteger(topLine),
        ValueFactory.newInteger(botLine),
        ValueFactory.newInteger(curLine),
        ValueFactory.newInteger(curCol),
      )
    )
  }

  object CursorGoto extends UpdateFactory {
    final val name = "cursor_goto"

    override def decode(v: Seq[Value]): Update = v match {
      case Seq(LongValue(row), LongValue(col)) =>
        CursorGoto(row = row.toInt, col = col.toInt)

      case other => sys.error(other.toString)
    }
  }
  case class CursorGoto(row: Int, col: Int) extends Update {
    override def name: String = CursorGoto.name

    override def param: Value = ArrayValue(
      Vector(
        ValueFactory.newInteger(row),
        ValueFactory.newInteger(col),
      )
    )
  }

  object EolClear extends UpdateFactory {
    final val name = "eol_clear"

    override def decode(v: Seq[Value]): Update = v match {
      case Seq() => EolClear()
      case other => sys.error(other.toString)
    }
  }
  case class EolClear() extends Update {
    override def name: String = EolClear.name

    override def param: Value = ValueFactory.newNil
  }

  object HighlightSet extends UpdateFactory {
    final val name = "highlight_set"

    private val attrFactories: Map[String, AttrFactory] = Set[AttrFactory](
      Foreground,
      Background,
      Special,
      Reverse,
      Italic,
      Bold,
      StrikeThrough,
      Underline,
      UnderCurl,
    ).iterator.map { f => (f.name, f) } .toMap

    def getAttr(name: String): scala.Option[AttrFactory] = attrFactories.get(name)

    trait AttrFactory {
      def name: String

      def decode(v: Value): HighlightSet.Attr
    }
    sealed trait Attr
    object Foreground extends AttrFactory {
      final val name = "foreground"

      override def decode(v: Value): Attr = v match {
        case LongValue(color) => Foreground(color.toInt)
      }
    }
    case class Foreground(color: Int) extends Attr
    object Background extends AttrFactory {
      final val name = "background"

      override def decode(v: Value): Attr = v match {
        case LongValue(color) => Background(color.toInt)
      }
    }
    case class Background(color: Int) extends Attr
    object Special extends AttrFactory {
      final val name = "special"

      override def decode(v: Value): Attr = v match {
        case LongValue(color) => Special(color.toInt)
      }
    }
    case class Special   (color: Int) extends Attr
    object Reverse extends AttrFactory {
      final val name = "reverse"

      override def decode(v: Value): Attr = Reverse() // XXX TODO -- do we need to check boolean?
    }
    case class Reverse      () extends Attr
    object Italic extends AttrFactory {
      final val name = "italic"

      override def decode(v: Value): Attr = Italic() // XXX TODO -- do we need to check boolean?
    }
    case class Italic       () extends Attr
    object Bold extends AttrFactory {
      final val name = "bold"

      override def decode(v: Value): Attr = Bold() // XXX TODO -- do we need to check boolean?
    }
    case class Bold         () extends Attr
    object StrikeThrough extends AttrFactory {
      final val name = "strikethrough"

      override def decode(v: Value): Attr = StrikeThrough() // XXX TODO -- do we need to check boolean?
    }
    case class StrikeThrough() extends Attr
    object Underline extends AttrFactory {
      final val name = "underline"

      override def decode(v: Value): Attr = Underline() // XXX TODO -- do we need to check boolean?
    }
    case class Underline    () extends Attr
    object UnderCurl extends AttrFactory {
      final val name = "undercurl"

      override def decode(v: Value): Attr = UnderCurl() // XXX TODO -- do we need to check boolean?
    }
    case class UnderCurl    () extends Attr

    override def decode(v: Seq[Value]): Update = v match {
      case Seq(MapValue(attrsV)) =>
        val attrs = attrsV.iterator.map { case (StringValue(key), valueV) =>
          val af = getAttr(key).getOrElse(sys.error(s"Unknown attribute $key"))
          val value = af.decode(valueV)
          value // (key, value)
        } .toSet
        HighlightSet(attrs)

      case other => sys.error(other.toString)
    }
  }
  case class HighlightSet(attrs: Set[HighlightSet.Attr]) extends Update {
    override def name: String = HighlightSet.name

    override def param: Value = ???
  }

  object Put extends UpdateFactory {
    final val name = "put"

    override def decode(v: Seq[Value]): Update = v match {
      case Seq(StringValue(text)) =>
        Put(text = text)

      case other => sys.error(other.toString)
    }
  }
  case class Put(text: String) extends Update {
    override def name: String = Put.name

    override def param: Value = StringValue(text)
  }

  object ModeInfoSet extends UpdateFactory {
    final val name = "mode_info_set"

    case class Info(name: String, shortName: String, cursor: Option[TextCursor])

    override def decode(v: Seq[Value]): Update = v match {
      case Seq(BooleanValue(cursorStyleEnabled), ArrayValue(infoSq)) =>
        val modeInfo = infoSq.iterator.map {
          case MapValue(entries) =>
            val name      = entries(StringValue("name"      )).toString
            val shortName = entries(StringValue("short_name")).toString
            val cursorShape = entries.get(StringValue("cursor_shape")).collect {
              case StringValue(TextCursor.Block      .name)  => TextCursor.Block
              case StringValue(TextCursor.Horizontal .name)  => TextCursor.Horizontal
              case StringValue(TextCursor.Vertical   .name)  => TextCursor.Vertical
            }
            val cursor = cursorShape.map { shape =>
              val cellPer   = entries.get(StringValue("cell_percentage" )).fold(0) { case LongValue(v) => v.toInt }
              val blinkWait = entries.get(StringValue("blinkwait"       )).fold(0) { case LongValue(v) => v.toInt }
              val blinkOn   = entries.get(StringValue("blinkon"         )).fold(0) { case LongValue(v) => v.toInt }
              val blinkOff  = entries.get(StringValue("blinkoff"        )).fold(0) { case LongValue(v) => v.toInt }
              TextCursor(shape, cellPercentage = cellPer,
                blinkWait = blinkWait, blinkOn = blinkOn, blinkOff = blinkOff)
            }
            Info(name = name, shortName = shortName, cursor = cursor)
        } .toIndexedSeq
        ModeInfoSet(cursorStyleEnabled = cursorStyleEnabled, modeInfo = modeInfo)

      case other => sys.error(other.toString)
    }
  }
  case class ModeInfoSet(cursorStyleEnabled: Boolean, modeInfo: ISeq[ModeInfoSet.Info]) extends Update {
    override def name: String = ModeInfoSet.name

    def asMap: Map[String, ModeInfoSet.Info] = modeInfo.iterator.map { i => (i.name, i) } .toMap

    override def param: Value = ???
  }

  object ModeChange extends UpdateFactory {
    final val name = "mode_change"

    override def decode(v: Seq[Value]): Update = v match {
      case Seq(StringValue(mode), LongValue(modeIdx)) =>
        ModeChange(mode = mode, modeIdx = modeIdx.toInt)

      case other => sys.error(other.toString)
    }
  }
  case class ModeChange(mode: String, modeIdx: Int) extends Update {
    override def name: String = ModeChange.name

    override def param: Value = ???
  }

  object MouseOn extends UpdateFactory {
    final val name = "mouse_on"

    override def decode(v: Seq[Value]): Update = v match {
      case Seq() => MouseOn()
      case other => sys.error(other.toString)
    }
  }
  case class MouseOn() extends Update {
    override def name: String = MouseOn.name

    override def param: Value = ValueFactory.newNil
  }

  object MouseOff extends UpdateFactory {
    final val name = "mouse_off"

    override def decode(v: Seq[Value]): Update = v match {
      case Seq() => MouseOff()
      case other => sys.error(other.toString)
    }
  }
  case class MouseOff() extends Update {
    override def name: String = MouseOff.name

    override def param: Value = ValueFactory.newNil
  }

  object SetScrollRegion extends UpdateFactory {
    final val name = "set_scroll_region"

    override def decode(v: Seq[Value]): Update = v match {
      case Seq(LongValue(top), LongValue(bot), LongValue(left), LongValue(right)) =>
        SetScrollRegion(top = top.toInt, bot = bot.toInt, left = left.toInt, right = right.toInt)

      case other => sys.error(other.toString)
    }
  }
  case class SetScrollRegion(top: Int, bot: Int, left: Int, right: Int) extends Update {
    override def name: String = SetScrollRegion.name

    override def param: Value = ???
  }

  object Scroll extends UpdateFactory {
    final val name = "scroll"

    override def decode(v: Seq[Value]): Update = v match {
      case Seq(LongValue(count)) =>
        Scroll(count = count.toInt)

      case other => sys.error(other.toString)
    }
  }
  case class Scroll(count: Int) extends Update {
    override def name: String = Scroll.name

    override def param: Value = ???
  }

  object BusyStart extends UpdateFactory {
    final val name = "busy_start"

    override def decode(v: Seq[Value]): Update = v match {
      case Seq() => BusyStart()
      case other => sys.error(other.toString)
    }
  }
  case class BusyStart() extends Update {
    override def name: String = BusyStart.name

    override def param: Value = ValueFactory.newNil
  }

  object BusyStop extends UpdateFactory {
    final val name = "busy_stop"

    override def decode(v: Seq[Value]): Update = v match {
      case Seq() => BusyStop()
      case other => sys.error(other.toString)
    }
  }
  case class BusyStop() extends Update {
    override def name: String = BusyStop.name

    override def param: Value = ValueFactory.newNil
  }

  object Flush extends UpdateFactory {
    final val name = "flush"

    override def decode(v: Seq[Value]): Update = v match {
      case Seq() => Flush()
      case other => sys.error(other.toString)
    }
  }
  case class Flush() extends Update {
    override def name: String = Flush.name

    override def param: Value = ValueFactory.newNil
  }
}
case class Redraw(updates: Vec[Redraw.Update]) extends Notification {
  override def method: String = Redraw.method

  override def params: ArrayValue = ???
}
