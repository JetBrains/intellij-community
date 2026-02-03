package com.intellij.lambda.testFramework.testApi

import com.intellij.lambda.testFramework.testApi.utils.defaultTestLatency
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.remoteDev.tests.LambdaIdeContext
import kotlin.time.Duration

context(lambdaIdeContext: LambdaIdeContext)
suspend fun callRenameAction(repeat: Int = 1, latency: Duration = defaultTestLatency) {
  callActionByShortcut(IdeActions.ACTION_RENAME, repeat, latency)
}