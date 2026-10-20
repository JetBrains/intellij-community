// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement
import com.intellij.util.ThreeState
import org.jetbrains.annotations.ApiStatus

/**
 * Reads the test runner settings of a module for the test code insight.
 *
 * The code insight does not depend on the test runners. The test runners implement this service.
 */
@ApiStatus.Internal
interface PyModuleTestRunnerQuery {
  /**
   * `true` if the module runs its tests with pytest: pytest is the selected runner, or the autodetection finds pytest.
   * `true` also when pytest is selected and the module has no SDK.
   */
  fun isPyTestEnabled(module: Module): Boolean

  /**
   * `true` if only the subclasses of `TestCase` are tests, as the "unittest" runner requires.
   * [explicit] other than [ThreeState.UNSURE] is the answer. [ThreeState.UNSURE] reads the runner of the module of [anchor].
   */
  fun isTestCaseClassRequired(anchor: PsiElement, explicit: ThreeState = ThreeState.UNSURE): Boolean

  companion object {
    @JvmStatic
    fun getInstance(): PyModuleTestRunnerQuery = service()
  }
}
