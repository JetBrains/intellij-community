package com.intellij.lambda.testFramework.testApi

import com.intellij.openapi.concurrency.awaitPromise
import com.intellij.remoteDev.tests.impl.utils.waitSuspending
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.JTree
import kotlin.time.Duration.Companion.seconds

fun JTree.containsText(text: String) = PlatformTestUtil.print(this, false).contains(text)

suspend fun JTree.expandAll(): JTree {
  waitSuspending("ExpandAll was successful", 15.seconds) {
    // can fail if during expand some node was deleted
    runCatching { TreeUtil.promiseExpandAll(this).awaitPromise(10.seconds) }.isSuccess
  }
  return this
}