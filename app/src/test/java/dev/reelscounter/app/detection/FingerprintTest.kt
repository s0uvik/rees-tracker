package dev.reelscounter.app.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintTest {
  @Test
  fun emptyInputHasNoFingerprint() {
    assertNull(Fingerprint.of(emptyList()))
    assertNull(Fingerprint.of(listOf(null, "", "   ")))
  }

  @Test
  fun isStableAndTrimmed() {
    assertEquals(Fingerprint.of(listOf("author", "caption")), Fingerprint.of(listOf(" author ", "caption\n")))
  }

  @Test
  fun differsForDifferentReels() {
    assertNotEquals(Fingerprint.of(listOf("author", "one")), Fingerprint.of(listOf("author", "two")))
  }

  @Test
  fun partBoundariesMatter() {
    assertNotEquals(Fingerprint.of(listOf("ab", "c")), Fingerprint.of(listOf("a", "bc")))
  }

  @Test
  fun neverContainsTheRawText() {
    val fp = Fingerprint.of(listOf("some_username", "my private caption"))!!
    assertEquals(32, fp.length)
    assertTrue(fp.all { it in '0'..'9' || it in 'a'..'f' })
    assertFalse(fp.contains("username"))
  }

  @Test
  fun indexFallback() {
    assertEquals("idx:4", Fingerprint.ofIndex(4))
  }
}
