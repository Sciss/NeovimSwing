/*
 * TextCursor.scala
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

object TextCursor {
  sealed trait Shape { def name: String }
  case object Block       extends Shape { final val name = "block"      }
  case object Horizontal  extends Shape { final val name = "horizontal" }
  case object Vertical    extends Shape { final val name = "vertical"   }
}
case class TextCursor(shape: TextCursor.Shape, cellPercentage: Int,
                      blinkWait: Int = 0, blinkOn: Int = 0, blinkOff: Int = 0) {
  def isBlinking: Boolean = blinkWait != 0 || blinkOn != 0 || blinkOff != 0

  def cellFraction: Float = cellPercentage * 0.01f
  def cellExtent(size: Int): Int = (cellPercentage * size) / 100
}