package com.intellij.lambda.testFramework.testApi.editor

import com.intellij.lambda.testFramework.frameworkLogger
import com.intellij.lambda.testFramework.testApi.callActionByShortcut
import com.intellij.lambda.testFramework.testApi.closeDialog
import com.intellij.lambda.testFramework.testApi.pressKeyStroke
import com.intellij.lambda.testFramework.testApi.utils.defaultTestLatency
import com.intellij.lambda.testFramework.testApi.waitCopiedExactly
import com.intellij.lambda.testFramework.testApi.waitCopiedNonEmptyString
import com.intellij.lambda.testFramework.testApi.waitForDialogWrapper
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.remoteDev.tests.LambdaIdeContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.swing.KeyStroke
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

context(lambdaIdeContext: LambdaIdeContext)
suspend fun EditorImpl.cutSelected(latency: Duration = defaultTestLatency) {
  delay(latency)
  val selected = selectedTextOrThrow()
  frameworkLogger.info("Going to cut '$selected'")
  callActionByShortcut(IdeActions.ACTION_EDITOR_CUT, latency = 0.milliseconds)
  waitCopiedExactly(selected)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun EditorImpl.copySelected(latency: Duration = defaultTestLatency) {
  delay(latency)
  val selected = selectedTextOrThrow()
  frameworkLogger.info("Going to copy '$selected'")
  callActionByShortcut(IdeActions.ACTION_EDITOR_COPY, latency = 0.milliseconds)
  waitCopiedExactly(selected)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun EditorImpl.paste(latency: Duration = defaultTestLatency) {
  delay(latency)
  val copied = waitCopiedNonEmptyString()
  frameworkLogger.info("Going to paste '$copied' to ${caretModel.currentCaret.caretModel.visualPosition}")
  callActionByShortcut(IdeActions.ACTION_EDITOR_PASTE,
                                                                                    latency = 0.milliseconds)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun EditorImpl.pasteFromHistory(contentPositionInHistory: Int, latency: Duration = defaultTestLatency) {
  assert(contentPositionInHistory in 1..9)
  delay(latency)
  frameworkLogger.info("Going to paste an item from clipboard history at position $contentPositionInHistory " +
                       "to ${caretModel.currentCaret.caretModel.visualPosition}")
  coroutineScope {
    launch(Dispatchers.EDT + ModalityState.any().asContextElement()) { // make sure code inside is executed in modal context
      callActionByShortcut(
        IdeActions.ACTION_EDITOR_PASTE_FROM_HISTORY) // shows modal dialog
      waitForDialogWrapper().closeDialog {
        pressKeyStroke(KeyStroke.getKeyStroke('0' + contentPositionInHistory))
      }
    }
  }
}