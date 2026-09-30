package dev.reelscounter.app.badge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeGeometryTest {
  // 1080x2400 portrait with 80px status bar and 130px nav bar.
  private val area = Area(left = 0, top = 80, right = 1080, bottom = 2270)
  private val w = 200
  private val h = 90
  private val margin = 10

  @Test
  fun clampKeepsBadgeFullyInside() {
    assertEquals(IntPoint(0, 80), BadgeGeometry.clamp(-50, -50, w, h, area))
    assertEquals(IntPoint(880, 2180), BadgeGeometry.clamp(5000, 5000, w, h, area))
    assertEquals(IntPoint(300, 900), BadgeGeometry.clamp(300, 900, w, h, area))
  }

  @Test
  fun clampNeverUnderflowsWhenBadgeIsBiggerThanArea() {
    val tiny = Area(0, 0, 100, 50)
    assertEquals(IntPoint(0, 0), BadgeGeometry.clamp(40, 40, w, h, tiny))
  }

  @Test
  fun snapsToNearestEdgeByCentre() {
    // centre x = 100 + 100 = 200 < 540 → left
    assertEquals(Snap(10, 500, Edge.LEFT), BadgeGeometry.snapToEdge(100, 500, w, h, area, margin))
    // centre x = 500 + 100 = 600 ≥ 540 → right
    assertEquals(Snap(870, 500, Edge.RIGHT), BadgeGeometry.snapToEdge(500, 500, w, h, area, margin))
  }

  @Test
  fun snapAlsoClampsVertically() {
    val snap = BadgeGeometry.snapToEdge(900, 2300, w, h, area, margin)
    assertEquals(Snap(870, 2180, Edge.RIGHT), snap)
    val top = BadgeGeometry.snapToEdge(0, 0, w, h, area, margin)
    assertEquals(Snap(10, 80, Edge.LEFT), top)
  }

  @Test
  fun snapRespectsHorizontalInsets() {
    // Landscape with the nav bar on the right and a cutout on the left.
    val landscape = Area(left = 100, top = 0, right = 2270, bottom = 1080)
    assertEquals(Edge.LEFT, BadgeGeometry.snapToEdge(150, 300, w, h, landscape, margin).edge)
    assertEquals(110, BadgeGeometry.snapToEdge(150, 300, w, h, landscape, margin).x)
    assertEquals(2060, BadgeGeometry.snapToEdge(2000, 300, w, h, landscape, margin).x)
  }

  @Test
  fun persistedPositionSurvivesRotation() {
    val portraitY = 80 + (2270 - 80 - h) / 2 // middle of the travel range
    val fraction = BadgeGeometry.yFraction(portraitY, h, area)
    assertEquals(0.5f, fraction, 0.001f)

    val landscape = Area(0, 60, 2400, 1080)
    val p = BadgeGeometry.fromPersisted(Edge.RIGHT, fraction, w, h, landscape, margin)
    assertEquals(2400 - w - margin, p.x)
    assertEquals(60 + (1080 - 60 - h) / 2, p.y)
  }

  @Test
  fun yFractionIsClampedAndSafeForZeroRange() {
    assertEquals(0f, BadgeGeometry.yFraction(-100, h, area), 0f)
    assertEquals(1f, BadgeGeometry.yFraction(99_999, h, area), 0f)
    assertEquals(0f, BadgeGeometry.yFraction(10, 100, Area(0, 0, 100, 100)), 0f)
  }

  @Test
  fun dismissTargetHitTest() {
    // Badge centre at (540, 2100); target centre at (540, 2150), radius 64.
    assertTrue(BadgeGeometry.isOverTarget(440, 2055, w, h, 540, 2150, 64))
    assertFalse(BadgeGeometry.isOverTarget(0, 500, w, h, 540, 2150, 64))
  }
}
