// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output

import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes.UpdateSourceLevelQuickFix
import org.junit.jupiter.api.Test

@TestApplication
@RunInEdt
class MavenQuickFixTests {
  companion object {
    private val tempDir = tempPathFixture()
    private val project = projectFixture(tempDir, openAfterCreation = false)
  }

  @Test
  fun testQuickFixSourceLevel() {
    MavenBuildToolLogTester.forProject(project.get())
      .withLines(*MavenBuildToolLogTestUtils.fromFile("org/jetbrains/maven/buildlogs/source-5-error-log.log"))
      .withSkippedOutput()
      .expect("Expected Quick Fix for source 5", QuickFixMatcher { it is UpdateSourceLevelQuickFix })
  }
}

class QuickFixMatcher(val matchFun: (BuildIssueQuickFix) -> Boolean) : BaseMatcher<BuildEvent>() {
  override fun describeTo(description: Description) {
    TODO("Not yet implemented")
  }

  override fun matches(item: Any?): Boolean {
    return item is BuildIssueEvent && item.issue.quickFixes.any(matchFun)
  }
}
