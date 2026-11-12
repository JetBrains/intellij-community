// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package de.plushnikov.intellij.plugin.action.lombok

import com.intellij.lombok.backend.action.lombok.LombokEqualsAndHashcodeAction
import com.intellij.openapi.actionSystem.AnAction
import de.plushnikov.intellij.plugin.action.LombokLightActionTestCase

class LombokEqualsAndHashcodeActionTest : LombokLightActionTestCase() {
  override fun getAction(): AnAction = LombokEqualsAndHashcodeAction()

  override fun getBasePath(): String = super.getBasePath() + "/action/lombok/equalsAndHashcode"

  fun testEqualsAndHashcodeSimple() {
    doTest()
  }
}
