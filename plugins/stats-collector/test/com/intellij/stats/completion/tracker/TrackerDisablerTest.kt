// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion.tracker

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.lang.java.JavaLanguage
import com.intellij.stats.completion.CompletionStatsPolicy
import junit.framework.TestCase

class TrackerDisablerTest : CompletionLoggingTestBase() {
  fun `test disabler works`() = doTest(true)

  fun `test tracked without disabler`() = doTest(false)

  private fun doTest(shouldDisable: Boolean) {
    val disabler = registerDisabler(shouldDisable)
    myFixture.completeBasic()
    myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
    TestCase.assertTrue(disabler.checked)
    if (shouldDisable) {
      TestCase.assertTrue(trackedEvents.isEmpty())
    }
    else {
      TestCase.assertFalse(trackedEvents.isEmpty())
    }
  }

  private fun registerDisabler(disabled: Boolean): TestDisabler {
    val disabler = TestDisabler(disabled)
    CompletionStatsPolicy.Instance.addExplicitExtension(JavaLanguage.INSTANCE, disabler, testRootDisposable)
    return disabler
  }

  private class TestDisabler(private val disabled: Boolean) : CompletionStatsPolicy {
    var checked: Boolean = false
    override fun isStatsLogDisabled(): Boolean {
      checked = true
      return disabled
    }
  }
}