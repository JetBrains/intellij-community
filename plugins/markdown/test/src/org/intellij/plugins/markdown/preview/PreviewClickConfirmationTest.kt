// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.preview

import org.intellij.plugins.markdown.ui.preview.PreviewClickConfirmation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The wire format between `PreviewClickGuard.js` and the IDE. Both ends are edited independently, and
 * getting it wrong fails silently: a flag that never reads as genuine shows up only as spurious dialogs,
 * one that always does removes the guard.
 */
class PreviewClickConfirmationTest {
  /** Only the first colon separates the flag; a link payload is full of them. */
  @Test
  fun `both flags parse, and the payload keeps its own colons`() {
    assertEquals(
      false to "http://example.com:8080/a:b",
      PreviewClickConfirmation.parseFlagPrefixed("0:http://example.com:8080/a:b")
    )
    assertEquals(true to "payload", PreviewClickConfirmation.parseFlagPrefixed("1:payload"))
  }

  /** Taking "http" for a flag would hand back "//example.com" and look like a successful parse. */
  @Test
  fun `a message with no flag is rejected rather than guessed`() {
    assertNull(PreviewClickConfirmation.parseFlagPrefixed("http://example.com"))
    assertNull(PreviewClickConfirmation.parseFlagPrefixed("payload"))
    assertNull(PreviewClickConfirmation.parseFlagPrefixed(""))
  }

  @Test
  fun `an unknown flag makes the whole message unrecognized`() {
    assertNull(PreviewClickConfirmation.parseFlagPrefixed("unexpected:payload"))
    assertNull(PreviewClickConfirmation.parseFlagPrefixed("2:payload"))
  }

  /** A guard that quietly stopped reporting must not read as a page full of trustworthy clicks. */
  @Test
  fun `anything other than a genuine click is confirmed`() {
    assertFalse(PreviewClickConfirmation.needsConfirmation(PreviewClickConfirmation.GENUINE_CLICK))
    assertTrue(PreviewClickConfirmation.needsConfirmation("1"))
    assertTrue(PreviewClickConfirmation.needsConfirmation(""))
    assertTrue(PreviewClickConfirmation.needsConfirmation(null))
  }
}
