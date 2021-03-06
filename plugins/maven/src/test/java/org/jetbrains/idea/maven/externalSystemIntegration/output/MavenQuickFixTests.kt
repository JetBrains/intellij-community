// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output

import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.testFramework.UsefulTestCase
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes.SourceOptionQuickFix
import org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes.UpdateSourceLevelQuickFix

class MavenQuickFixTests : MavenBuildToolLogTestUtils() {


  fun testQuickFixSourceLevel() {
      testCase(*fromFile("org/jetbrains/maven/buildlogs/source-5-error-log.log"))
        .withSkippedOutput()
        .expect("Expected Quick Fix for source 5",  QuickFixMatcher{it is UpdateSourceLevelQuickFix })

  }
}
class QuickFixMatcher(val matchFun:(BuildIssueQuickFix)->Boolean): BaseMatcher<BuildEvent>() {
  override fun describeTo(description: Description) {
    TODO("Not yet implemented")
  }

  override fun matches(item: Any?): Boolean {
    return item is BuildIssueEvent && item.issue.quickFixes.any (matchFun)
  }

}