package org.jetbrains.plugins.textmate.regex

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

abstract class RegexFacadeTest {
  @Test
  fun matching() {
    withRegex("[0-9]+") { regex ->
      withString("12:00pm") { string ->
        val match = regex.match(string, null)
        assertEquals(TextMateCharRange(0.charOffset(), 2.charOffset()), match.charRange(string))
      }
    }
  }

  @Test
  fun matchingFromPosition() {
    withRegex("[0-9]+") { regex ->
      withString("12:00pm") { string ->
        val match = regex.match(string, 2.byteOffset(), matchBeginPosition = true, matchBeginString = true, checkCancelledCallback = null)
        assertEquals(TextMateCharRange(3.charOffset(), 5.charOffset()), match.charRange(string))
      }
    }
  }

  @Test
  fun matchingWithGroups() {
    withRegex("([0-9]+):([0-9]+)") { regex ->
      withString("12:00pm") { string ->
        val match = regex.match(string, null)
        assertEquals(TextMateCharRange(0.charOffset(), 5.charOffset()), match.charRange(string))
        assertEquals(TextMateCharRange(0.charOffset(), 2.charOffset()), match.charRange(string, 1))
        assertEquals(TextMateCharRange(3.charOffset(), 5.charOffset()), match.charRange(string, 2))
      }
    }
  }

  @Test
  fun cyrillicMatchingSinceIndex() {
    withRegex("мир") { regex ->
      val text = "привет, мир; привет, мир!"
      withString(text) { string ->
        val match = regex.match(string, byteOffsetByCharOffset(text, 0.charOffset(), 9.charOffset()), true, true, null)
        assertEquals(TextMateCharRange(21.charOffset(), 24.charOffset()), match.charRange(string))
      }
    }
  }

  @Test
  fun cyrillicMatching() {
    withRegex("мир") { regex ->
      withString("привет, мир!") { string ->
        val match = regex.match(string, null)
        assertEquals(TextMateCharRange(8.charOffset(), 11.charOffset()), match.charRange(string))
      }
    }
  }

  @Test
  fun unicodeMatching() {
    withRegex("мир") { regex ->
      val string = "\uD83D\uDEA7\uD83D\uDEA7\uD83D\uDEA7 привет, мир!"
      withString(string) { textMateString ->
        val match = regex.match(textMateString, null)
        val range = match.charRange(textMateString)
        assertEquals("мир", string.subSequence(range.start, range.end))
      }
    }
  }

  @Test
  fun matchBeginPosition() {
    withRegex("\\Gbar") { regex ->
      withString("foo bar") { string ->
        val noBeginMatch = regex.match(string, 4.byteOffset(), matchBeginPosition = false, matchBeginString = true, null)
        assertFalse(noBeginMatch.matched)

        val beginMatch = regex.match(string, 4.byteOffset(), matchBeginPosition = true, matchBeginString = true, null)
        assertTrue(beginMatch.matched)
      }
    }
  }

  @Disabled("Joni doesn't support disabling \\A")
  @Test
  fun matchBeginString() {
    withRegex("\\Afoo") { regex ->
      withString("foo bar") { string ->
        val noBeginMatch = regex.match(string, 0.byteOffset(), matchBeginPosition = true, matchBeginString = false, null)
        assertFalse(noBeginMatch.matched)

        val beginMatch = regex.match(string, 0.byteOffset(), matchBeginPosition = true, matchBeginString = true, null)
        assertTrue(beginMatch.matched)
      }
    }
  }

  private fun withRegex(pattern: String, body: (RegexFacade) -> Unit) {
    return withRegexProvider {
      it.withRegex(pattern, body)
    }
  }

  private fun withString(s: String, body: (TextMateString) -> Unit) {
    return withRegexProvider {
      it.withString(s, body)
    }
  }

  abstract fun <T> withRegexProvider(body: (RegexProvider) -> T): T
}
