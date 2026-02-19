package com.intellij.lambda.testFramework.testApi.editor

import com.intellij.lambda.testFramework.testApi.requestAndWaitFocus
import com.intellij.lambda.testFramework.testApi.waitForFocus
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.remoteDev.tests.LambdaIdeContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

context(lambdaIdeContext: LambdaIdeContext)
suspend fun FileEditor.waitForFocus(timeout: Duration = 10.seconds) =
  editorImpl?.contentComponent?.waitForFocus("editor $this", timeout)
  ?: error("Proper waiting for focus is not supported for editors without contentComponent")

context(lambdaIdeContext: LambdaIdeContext)
suspend fun FileEditor.requestAndWaitFocus() {
  editorImpl?.contentComponent?.requestAndWaitFocus("editor $this")
  ?: error("Proper request focus is not supported for editors without contentComponent")
}