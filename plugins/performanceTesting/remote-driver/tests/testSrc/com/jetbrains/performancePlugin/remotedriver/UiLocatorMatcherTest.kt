// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.remotedriver

import com.intellij.driver.model.ui.UiLocator
import com.intellij.driver.model.ui.UiNode
import com.intellij.driver.model.ui.UiRole
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import javax.swing.JPasswordField
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities

class UiLocatorMatcherTest {
  @Test
  fun `accessible name is exact by default`() {
    val root = root(
      node(1, name = "Run"),
      node(2, name = "Run Anything"),
    )

    assertThat(UiLocatorMatcher.find(UiLocator.AccessibleName("Run"), root)).containsExactly(1)
    assertThat(UiLocatorMatcher.find(UiLocator.AccessibleName("run", exact = false), root)).containsExactly(1, 2)
  }

  @Test
  fun `role locator can include accessible name`() {
    val root = root(
      node(1, role = UiRole.BUTTON, name = "Cancel"),
      node(2, role = UiRole.BUTTON, name = "OK"),
      node(3, role = UiRole.LABEL, name = "OK"),
    )

    assertThat(UiLocatorMatcher.find(UiLocator.Role(UiRole.BUTTON, "OK"), root)).containsExactly(2)
  }

  @Test
  fun `within only returns descendants of matching ancestor`() {
    val root = root(
      node(1, name = "First", children = listOf(node(2, role = UiRole.BUTTON, name = "OK"))),
      node(3, name = "Second", children = listOf(node(4, role = UiRole.BUTTON, name = "OK"))),
    )
    val locator = UiLocator.Within(
      UiLocator.AccessibleName("Second"),
      UiLocator.Role(UiRole.BUTTON, "OK"),
    )

    assertThat(UiLocatorMatcher.find(locator, root)).containsExactly(4)
  }

  @Test
  fun `nth explicitly selects one duplicate`() {
    val root = root(
      node(1, texts = listOf("item")),
      node(2, texts = listOf("item")),
    )

    assertThat(UiLocatorMatcher.find(UiLocator.Nth(UiLocator.Text("item"), 1), root)).containsExactly(2)
    assertThatIllegalArgumentException().isThrownBy {
      UiLocatorMatcher.find(UiLocator.Nth(UiLocator.Text("item"), -1), root)
    }
  }

  @Test
  fun `raw XPath remains an escape hatch`() {
    val root = root(node(1), node(2))

    assertThat(UiLocatorMatcher.find(UiLocator.Xpath("//div"), root) { setOf(2L) }).containsExactly(2)
  }

  @Test
  fun `accessible text replacement preserves exact Unicode text`() {
    val field = JTextField("before")
    val unicodeText = "ui-probe \u043A\u0438\u0440\u0438\u043B\u043B\u0438\u0446\u0430"

    SwingUtilities.invokeAndWait { replaceAccessibleText(field, unicodeText) }

    assertThat(field.text).isEqualTo(unicodeText)
  }

  @Test
  fun `accessible text replacement supports clearing`() {
    val field = JTextField("before")

    SwingUtilities.invokeAndWait { replaceAccessibleText(field, "") }

    assertThat(field.text).isEmpty()
  }

  @Test
  fun `accessible text read exposes exact editable value`() {
    val field = JTextArea("fun main() {}\n")

    val text = readAccessibleTextOrNull(field, UiRole.TEXT)

    assertThat(text).isEqualTo("fun main() {}\n")
  }

  @Test
  fun `accessible text read does not expose password value`() {
    val field = JPasswordField("secret")

    val text = readAccessibleTextOrNull(field, UiRole.PASSWORD_TEXT)

    assertThat(text).isNull()
  }

  private fun root(vararg children: UiNode): UiNode = node(0, children = children.toList())

  private fun node(
    id: Long,
    role: UiRole = UiRole.UNKNOWN,
    name: String? = null,
    texts: List<String> = emptyList(),
    children: List<UiNode> = emptyList(),
  ): UiNode {
    return UiNode(
      id = id,
      className = "Node$id",
      javaClassName = "example.Node$id",
      role = role,
      name = name,
      description = null,
      texts = texts,
      states = emptySet(),
      actions = emptyList(),
      value = null,
      bounds = null,
      attributes = emptyMap(),
      children = children,
    )
  }
}
