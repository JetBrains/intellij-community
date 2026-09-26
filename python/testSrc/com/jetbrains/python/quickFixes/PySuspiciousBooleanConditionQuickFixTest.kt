// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.quickFixes

import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyQuickFixTestCase
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.inspections.PySuspiciousBooleanConditionInspection

@Subsystems.QuickFixes
@Layers.Functional
class PySuspiciousBooleanConditionQuickFixTest : PyQuickFixTestCase() {

  fun testAddAwaitInIfCondition() {
    doQuickFixTest(PySuspiciousBooleanConditionInspection::class.java, PyPsiBundle.message("QFIX.add.await"))
  }
}
