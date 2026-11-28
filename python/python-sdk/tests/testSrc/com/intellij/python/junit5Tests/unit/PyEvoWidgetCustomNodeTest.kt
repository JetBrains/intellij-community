// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.icons.AllIcons
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.intellij.idea.TestFor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeItem
import com.intellij.python.sdk.frontend.evolution.components.EvoTreeLeafElement
import com.intellij.python.sdk.frontend.evolution.components.State
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Guards the trailing column of a row of the "Custom" node of the interpreter widget. A Pro entry shows its lock there,
 * as in the other "Add Interpreter" lists.
 */
@TestApplication
@TestFor(issues = ["PY-90313"])
@Subsystems.Interpreters
@Layers.Functional
class PyEvoWidgetCustomNodeTest {
  @Test
  @DisplayName("a row with a secondary icon shows it in the trailing column")
  fun rowWithSecondaryIcon() {
    val action = object : AnAction("On Docker…") {
      override fun actionPerformed(e: AnActionEvent) = Unit
    }
    action.templatePresentation.putClientProperty(ActionUtil.SECONDARY_ICON, AllIcons.Ultimate.PycharmLock)
    val item = EvoTreeItem(EvoTreeLeafElement(action).apply { state = State.DONE })

    assertThat(item.statusIcon).isSameAs(AllIcons.Ultimate.PycharmLock)
  }

  @Test
  @DisplayName("a row without a secondary icon shows nothing in the trailing column")
  fun rowWithoutSecondaryIcon() {
    val action = object : AnAction("Add Local Interpreter…") {
      override fun actionPerformed(e: AnActionEvent) = Unit
    }
    val item = EvoTreeItem(EvoTreeLeafElement(action).apply { state = State.DONE })

    assertThat(item.statusIcon).isNull()
  }
}