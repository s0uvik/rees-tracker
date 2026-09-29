package expo.modules.reelstracker.badge

import kotlin.math.roundToInt

/** Screen-space rectangle the badge may occupy (screen minus system bars / cutouts). */
data class Area(val left: Int, val top: Int, val right: Int, val bottom: Int) {
  val width get() = right - left
  val height get() = bottom - top
}

data class IntPoint(val x: Int, val y: Int)

enum class Edge { LEFT, RIGHT }

data class Snap(val x: Int, val y: Int, val edge: Edge)

/**
 * Pure positioning math for the floating badge. Coordinates are the window's
 * top-left corner in pixels, as used by WindowManager.LayoutParams x/y with
 * Gravity.TOP | Gravity.START.
 */
object BadgeGeometry {

  fun clamp(x: Int, y: Int, width: Int, height: Int, area: Area): IntPoint {
    val maxX = (area.right - width).coerceAtLeast(area.left)
    val maxY = (area.bottom - height).coerceAtLeast(area.top)
    return IntPoint(x.coerceIn(area.left, maxX), y.coerceIn(area.top, maxY))
  }

  /** Snap horizontally to whichever edge the badge's centre is closer to, keeping it inside [area]. */
  fun snapToEdge(x: Int, y: Int, width: Int, height: Int, area: Area, marginPx: Int): Snap {
    val centerX = x + width / 2
    val edge = if (centerX < area.left + area.width / 2) Edge.LEFT else Edge.RIGHT
    val p = positionOnEdge(edge, y, width, height, area, marginPx)
    return Snap(p.x, p.y, edge)
  }

  fun positionOnEdge(edge: Edge, y: Int, width: Int, height: Int, area: Area, marginPx: Int): IntPoint {
    val targetX = when (edge) {
      Edge.LEFT -> area.left + marginPx
      Edge.RIGHT -> area.right - width - marginPx
    }
    return clamp(targetX, y, width, height, area)
  }

  /** Vertical position as a 0..1 fraction of the travel range, so it survives rotation. */
  fun yFraction(y: Int, height: Int, area: Area): Float {
    val range = area.height - height
    if (range <= 0) return 0f
    return ((y - area.top).toFloat() / range).coerceIn(0f, 1f)
  }

  fun fromPersisted(edge: Edge, yFraction: Float, width: Int, height: Int, area: Area, marginPx: Int): IntPoint {
    val range = (area.height - height).coerceAtLeast(0)
    val y = area.top + (yFraction.coerceIn(0f, 1f) * range).roundToInt()
    return positionOnEdge(edge, y, width, height, area, marginPx)
  }

  fun isOverTarget(
    badgeX: Int,
    badgeY: Int,
    badgeWidth: Int,
    badgeHeight: Int,
    targetCenterX: Int,
    targetCenterY: Int,
    radiusPx: Int,
  ): Boolean {
    val dx = (badgeX + badgeWidth / 2 - targetCenterX).toLong()
    val dy = (badgeY + badgeHeight / 2 - targetCenterY).toLong()
    return dx * dx + dy * dy <= radiusPx.toLong() * radiusPx
  }

  /** Default placement: right edge, a third of the way down. */
  const val DEFAULT_Y_FRACTION = 0.33f
  val DEFAULT_EDGE = Edge.RIGHT
}
