package com.intellij.sh.run

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ShRunLineMarkerTest : BasePlatformTestCase() {
  fun testRunMarkerPresenceInSmartMode() {
    myFixture.configureByText("script.sh", "#!/usr/bin/env bash")
    assertOneElement(myFixture.findAllGutters("script.sh"))
  }

  fun testRunMarkerPresenceInDumbMode() {
    myFixture.configureByText("script.sh", "#!/usr/bin/env bash")
    (DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl).mustWaitForSmartMode(false, getTestRootDisposable())
    DumbModeTestUtils.runInDumbModeSynchronously(project) {
      assertOneElement(myFixture.findAllGutters("script.sh"))
    }
  }
}