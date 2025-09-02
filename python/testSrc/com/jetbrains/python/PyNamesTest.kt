// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PyNamesTest {
  @Test
  fun testIsProtected() {
    assertTrue(isProtected("_x"))
    assertFalse(isProtected(""))
    assertFalse(isProtected("_"))
    assertFalse(isProtected("__"))
    assertFalse(isProtected("___"))
    assertFalse(isProtected("___"))
    assertFalse(isProtected("____"))
    assertFalse(isProtected("_x_"))
    assertFalse(isProtected("__x"))
    assertFalse(isProtected("__x__"))
    assertFalse(isProtected("___x___"))
    assertFalse(isProtected("x_"))
    assertFalse(isProtected("x__"))
  }

  @Test
  fun testIsPrivate() {
    assertTrue(isPrivate("__x"))
    assertFalse(isPrivate(""))
    assertFalse(isPrivate("_"))
    assertFalse(isPrivate("__"))
    assertFalse(isPrivate("___"))
    assertFalse(isPrivate("___"))
    assertFalse(isPrivate("____"))
    assertFalse(isPrivate("_x"))
    assertFalse(isPrivate("_x_"))
    assertFalse(isPrivate("__x__"))
    assertFalse(isPrivate("___x___"))
    assertFalse(isPrivate("x_"))
    assertFalse(isPrivate("x__"))
  }

  @Test
  fun testIsSunder() {
    assertTrue(isSunder("_x_"))
    assertFalse(isSunder(""))
    assertFalse(isSunder("_"))
    assertFalse(isSunder("__"))
    assertFalse(isSunder("___"))
    assertFalse(isSunder("___"))
    assertFalse(isSunder("____"))
    assertFalse(isSunder("_x"))
    assertFalse(isSunder("__x"))
    assertFalse(isSunder("__x__"))
    assertFalse(isSunder("___x___"))
    assertFalse(isSunder("x_"))
    assertFalse(isSunder("x__"))
  }

  @Test
  fun testIsDunder() {
    assertTrue(isDunder("__x__"))
    assertFalse(isDunder(""))
    assertFalse(isDunder("_"))
    assertFalse(isDunder("__"))
    assertFalse(isDunder("___"))
    assertFalse(isDunder("___"))
    assertFalse(isDunder("____"))
    assertFalse(isDunder("_x"))
    assertFalse(isDunder("__x"))
    assertFalse(isDunder("_x_"))
    assertFalse(isDunder("___x___"))
    assertFalse(isDunder("x_"))
    assertFalse(isDunder("x__"))
  }
}
