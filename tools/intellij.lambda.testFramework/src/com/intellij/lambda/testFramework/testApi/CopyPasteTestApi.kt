package com.intellij.lambda.testFramework.testApi

import com.intellij.lambda.testFramework.frameworkLogger
import com.intellij.lambda.testFramework.testApi.utils.defaultTestLatency
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.remoteDev.tests.impl.utils.waitSuspending
import kotlinx.coroutines.delay
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


val copiedString: String?
  get() = CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)

val clipboardHistory: List<String>
  get() = CopyPasteManager.getInstance().allContents.map {
    try {
      it.getTransferData(DataFlavor.stringFlavor) as String
    }
    catch (e: Exception) {
      "<nonTextContent>"
    }
  }

fun clearCopyBuffer() {
  CopyPasteManager.getInstance().setContents(StringSelection(""))
}

suspend fun waitCopiedNonEmptyString(timeout: Duration = 15.seconds): String {
  val copiedString = waitSuspending("CopyPasteManager received the String",
                                                                                             timeout,
                                                                                             getter = { copiedString },
                                                                                             checker = { it != null && it.isNotEmpty() })!!
  frameworkLogger.info("Copied '$copiedString'")
  return copiedString
}

suspend fun waitCopiedExactly(expectedCopied: String, timeout: Duration = 15.seconds) {
  waitSuspending("CopyPasteManager received the expected '$expectedCopied'}",
                                                                          timeout,
                                                                          getter = { copiedString },
                                                                          checker = { it == expectedCopied })
}

suspend fun waitClipboardHistory(expectedHistory: List<String>, timeout: Duration = 15.seconds) {
  waitSuspending(
    subjectOfWaiting = "CopyPasteManager has the expected history $expectedHistory",
    timeout = timeout,
    getter = { clipboardHistory },
    checker = { it == expectedHistory })
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun cut(expectedCut: String? = null, latency: Duration = defaultTestLatency) {
  delay(latency)
  frameworkLogger.info("Going to cut")
  callActionByShortcut(IdeActions.ACTION_CUT, latency = 0.milliseconds)
  if (expectedCut == null) {
    waitCopiedNonEmptyString()
  } else {
    waitCopiedExactly(expectedCut)
  }
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun copy(expectedCopied: String? = null, latency: Duration = defaultTestLatency) {
  delay(latency)
  frameworkLogger.info("Going to copy")
  callActionByShortcut(IdeActions.ACTION_COPY, latency = 0.milliseconds)
  if (expectedCopied == null) {
    waitCopiedNonEmptyString()
  } else {
    waitCopiedExactly(expectedCopied)
  }
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun paste(latency: Duration = defaultTestLatency) {
  delay(latency)
  val copied = waitCopiedNonEmptyString()
  frameworkLogger.info("Going to paste '$copied'")
  callActionByShortcut(IdeActions.ACTION_PASTE, latency = 0.milliseconds)
}