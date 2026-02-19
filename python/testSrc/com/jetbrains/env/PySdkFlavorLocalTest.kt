// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env

import com.intellij.testFramework.RuleChain
import com.jetbrains.env.python.PySDKRule
import org.junit.Rule

class PySdkFlavorLocalTest : PySdkFlavorTestBase() {
  override val sdkRule: PySDKRule = PySDKRule(null)

  @Rule
  @JvmField
  val ruleChain: RuleChain = RuleChain(projectRule, sdkRule)
}