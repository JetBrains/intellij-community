// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.ide.ui.icons.rpcId
import com.intellij.python.sdk.common.PyInterpreterItem
import com.intellij.python.sdk.common.PyInterpreterRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import javax.swing.ImageIcon

/**
 * What makes two interpreter list rows the same row.
 *
 * A combo box matches its own selection by equality, and an item carries an `IconId`, whose equality is identity. So
 * an item must compare by the interpreter it names and nothing else, or selecting an interpreter in a rebuilt list
 * silently fails.
 */
class PyInterpreterItemTest {
  @Test
  @DisplayName("two rows for the same interpreter are equal, whatever they draw")
  fun `items are equal by ref`() {
    val one = item("myenv", "Python 3.12")
    val other = item("myenv", "a different label")

    assertEquals(one, other)
    assertEquals(one.hashCode(), other.hashCode())
    assertNotEquals(one.name, other.name)
    assertNotEquals(one.icon, other.icon)
  }

  @Test
  @DisplayName("rows for two interpreters are different rows")
  fun `items differ by ref`() {
    assertNotEquals(item("myenv", "Python 3.12"), item("otherenv", "Python 3.12"))
  }

  private fun item(sdkName: String, label: String) = PyInterpreterItem(
    ref = PyInterpreterRef.ExistingSdk(sdkName),
    name = label,
    suffix = null,
    description = "/envs/$sdkName/bin/python",
    problem = null,
    // A fresh IconId every time, which is what a rebuilt list produces.
    icon = ImageIcon().rpcId(),
    isPathDerivedName = false,
  )
}
