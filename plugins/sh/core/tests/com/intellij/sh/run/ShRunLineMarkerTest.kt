package com.intellij.sh.run

import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl

class ShRunLineMarkerTest : BasePlatformTestCase() {
  fun testRunMarkerPresenceInSmartMode() {
    myFixture.configureByText("script.sh", "#!/usr/bin/env bash")
    assertOneElement(myFixture.findAllGutters("script.sh"))
  }

  fun testRunMarkerPresenceInDumbMode() {
    myFixture.configureByText("script.sh", "#!/usr/bin/env bash")
    CodeInsightTestFixtureImpl.mustWaitForSmartMode(false, getTestRootDisposable())
    DumbModeTestUtils.runInDumbModeSynchronously(project) {
      assertOneElement(myFixture.findAllGutters("script.sh"))
    }
  }
}