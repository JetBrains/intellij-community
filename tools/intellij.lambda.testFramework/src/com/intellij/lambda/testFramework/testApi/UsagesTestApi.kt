package com.intellij.lambda.testFramework.testApi

import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.remoteDev.tests.impl.utils.waitSuspending
import com.intellij.remoteDev.tests.impl.utils.waitSuspendingNotNull
import com.intellij.usages.UsageViewManager
import org.assertj.core.api.Assertions
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

suspend fun LambdaIdeContext.waitUsagesAreShownDefault(expectedUsage: String,
                                                           timeout: Duration = 30.seconds,
                                                           openUsagesAction: suspend () -> Unit) {
  openUsagesAction()

  val selectedUsageView = waitSuspendingNotNull("'Find usages window' is opened",
                                                                                                         timeout) {
    UsageViewManager.getInstance(getProject()).selectedUsageView
  }
  waitSuspending("'Find usages' is finished", timeout) {
    !selectedUsageView.isSearchInProgress
  }
  Assertions
    .assertThat(selectedUsageView.usages.count())
    .describedAs("Expecting 1 usage")
    .isEqualTo(1)
  Assertions
    .assertThat(selectedUsageView.usages.toList()[0].presentation.plainText)
    .describedAs("Single result of find usages search should contain '$expectedUsage'")
    .contains(expectedUsage)
}
