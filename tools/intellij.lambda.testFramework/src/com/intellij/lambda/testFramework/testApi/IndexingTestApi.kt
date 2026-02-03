package com.intellij.lambda.testFramework.testApi

import com.intellij.lambda.testFramework.frameworkLogger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.remoteDev.tests.impl.utils.runLogged
import com.intellij.remoteDev.tests.impl.utils.waitSuspending
import com.intellij.testFramework.IndexingTestUtil
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

context(lambdaIdeContext: LambdaIdeContext)
suspend fun waitForIndexingFinished(timeout: Duration) {
  val project = getProject()
  runLogged("Waiting for indexing finish", timeout) {
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
    delay(300.milliseconds)
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
  }
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun waitForIndexingAndProgressesFinished(timeout: Duration) {
  val project = getProject()

  waitSuspending("Waiting for indexing and progresses finish", timeout) {
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
    val first = !hasProgresses() && !project.isIndexing()
    delay(300.milliseconds)
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
    val second = !hasProgresses() && !project.isIndexing()
    first && second
  }
}

context(lambdaIdeContext: LambdaIdeContext)
fun Project.isIndexing(): Boolean {
  val isIndexing = DumbService.isDumb(this)
  frameworkLogger.info("Indexing is running: ${isIndexing}")
  return isIndexing
}