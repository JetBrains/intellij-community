package com.intellij.lambda.testFramework.testApi.editor

import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer
import com.intellij.lambda.testFramework.frameworkLogger
import com.intellij.lambda.testFramework.testApi.utils.defaultTestLatency
import com.intellij.lambda.testFramework.testApi.utils.findFirstDifference
import com.intellij.lambda.testFramework.testApi.utils.formatDifference
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.remoteDev.tests.LambdaBackendContext
import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.remoteDev.tests.impl.utils.waitSuspending
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import java.io.File
import javax.swing.JLabel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

context(lambdaIdeContext: LambdaIdeContext)
val Project.allOpenFileEditors: List<FileEditor>
  get() = FileEditorManager.getInstance(this).allEditors.asList()

context(lambdaIdeContext: LambdaIdeContext)
val Project.selectedFileEditor: FileEditor?
  get() = FileEditorManager.getInstance(this).selectedEditor

context(lambdaIdeContext: LambdaIdeContext)
val Project.selectedFileEditorOrThrow: FileEditor
  get() = selectedFileEditor ?: error("Couldn't find opened FileEditor")

val FileEditor.editorImpl: EditorImpl?
  get() = (this as? TextEditor)?.editor as? EditorImpl

val FileEditor.editorImplOrThrow: EditorImpl
  get() = editorImpl ?: error("Couldn't find opened EditorImpl")

context(lambdaIdeContext: LambdaIdeContext)
val Project.selectedFileName: String?
  get() = selectedFileEditor?.file?.name

suspend fun EditorImpl.waitEditorIsLoaded() {
  waitSuspending("Editor is properly loaded", 10.seconds) {
    AsyncEditorLoader.isEditorLoaded(this)
  }
}

val EditorImpl.trafficLineRenderOrNull
  get() = ((markupModel as EditorMarkupModelImpl).errorStripeRenderer as? TrafficLightRenderer)

val EditorImpl.trafficLineRender
  get() = trafficLineRenderOrNull ?: error("Traffic line renderer is not available")

context(lambdaIdeContext: LambdaIdeContext)
suspend fun EditorImpl.waitTrafficLineRenderReady() {
  val toolbar = (markupModel as? EditorMarkupModelImpl)?.statusToolbar?.component
  waitSuspending("Traffic line render doesn't contain any text labels", 10.seconds,
                 getter = { readAction { trafficLineRenderOrNull?.status?.expandedStatus } },
                 checker = { it == null || it.all { status -> !status.text.contains("[A-Za-z]".toRegex()) || status.text == "Reader Mode" } })

  if (lambdaIdeContext is LambdaBackendContext) {
    return
  }
  else {
    waitSuspending("Traffic line render contains some visible labels", 10.seconds,
                   getter = { UIUtil.uiTraverser(toolbar).filter { it is JLabel }.filter { it.isVisible } },
                   checker = { it.toSet().isNotEmpty() })
  }
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun EditorImpl.waitForIndexingOrAnalyzing(timeout: Duration = 15.seconds, project: Project?) {
  val expectedStatuses = listOf("Indexing", "Analyzing")
  fun String.startsWithAny(prefixes: List<String>) = prefixes.any { this.trim().startsWith(it, ignoreCase = true) }
  try {
    frameworkLogger.info("Project dumb mode status before waiting for state ${DumbService.isDumb(project!!)}")
  }
  catch (e: Exception) {
    frameworkLogger.warn("Exception while getting project dumb mode status before waiting for state", e)
  }
  withContext(Dispatchers.IO) {
    waitSuspending("There is 'Indexing' or 'Analyzing' in analyzer status", timeout, delay = 1.seconds,
                   getter = { readAction { trafficLineRenderOrNull?.status?.expandedStatus } },
                   checker = {
                     if (it == null) {
                       frameworkLogger.info("Traffic line render is null")
                       false
                     }
                     else {
                       it.forEach { status -> frameworkLogger.info("Status text item: ${status.text}") }
                       it.any { status -> status.text.startsWithAny(expectedStatuses) }
                     }
                   })
  }
}

context(lambdaIdeContext: LambdaIdeContext)
val EditorImpl.fileName
  get() = virtualFile?.name

/**
 * Be careful when typing directly to the editor. In most cases `typeWithEventQueue` is a safer choice.
 */
suspend fun EditorImpl.typeWithLatency(string: CharSequence, latency: Duration = defaultTestLatency) {
  frameworkLogger.info("Type with latency ($latency): '$string'")
  for (c in string) {
    delay(latency)
    writeIntentReadAction {
      type(c.toString())
    }
  }
}

suspend fun EditorImpl.textFromBeginningTillCaret(): String =
  readAction { document.getText(TextRange(0, caretModel.offset)) }

suspend fun EditorImpl.waitContains(text: String, timeout: Duration = 20.seconds) {
  waitSuspending("Document contains '$text'", timeout,
                 getter = { document.text }, checker = { it.contains(text) })
}

fun EditorImpl.documentContains(text: String): Boolean {
  return document.text.contains(text)
}

suspend fun EditorImpl.waitTextMatches(text: String, timeout: Duration = 20.seconds) {
  waitSuspending("Document matches '$text'", timeout,
                 getter = { document.text }, checker = { it == text })
}

fun EditorImpl.textMatches(text: String): Boolean {
  return document.text.equals(text)
}

suspend fun EditorImpl.waitForTextEqualsGoldOne(goldFile: File, timeout: Duration = 30.seconds) {
  doWaitForTextEqualsGoldOneAndPump(goldFile, timeout) { expectedText, actualText ->
    findFirstDifference(expectedText, actualText) == null
  }
}

private fun String.normalizeLineEndings(): String {
  return this.replace("\r\n", "\n")
}

suspend fun EditorImpl.doWaitForTextEqualsGoldOneAndPump(goldFile: File, duration: Duration, checker: (String, String) -> Boolean) {
  val expectedFileText = goldFile.readText().normalizeLineEndings()
  waitSuspending("Text equals one from '${goldFile.canonicalPath}'",
                 duration,
                 getter = { document.text },
                 checker = { checker(expectedFileText, it) },
                 failMessageProducer = {
                   val currentFileText = it!!.normalizeLineEndings()
                   val difference = findFirstDifference(expectedFileText, currentFileText)
                   "expected:\n" + expectedFileText +
                   "\ncurrent:\n" + currentFileText + "" +
                   "\ndifference: " + formatDifference(difference!!)
                 })
}


suspend fun EditorImpl.waitVisible(visualPosition: VisualPosition, timeout: Duration = 30.seconds) {
  waitSuspending("Visual position $visualPosition is visible", timeout,
                 getter = { scrollingModel.visibleArea },
                 checker = { it.contains(visualPositionToXY(visualPosition)) }
  )
}

fun EditorImpl.isVisible(visualPosition: VisualPosition): Boolean {
  return scrollingModel.visibleArea.contains(visualPositionToXY(visualPosition))
}

fun EditorImpl.assertVisible(visualPosition: VisualPosition) {
  assertThat(scrollingModel.visibleArea).satisfies({ it.contains(visualPositionToXY(visualPosition)) })
}

suspend fun EditorImpl.waitVisibleAreaIsNotChanging(timeout: Duration = 40.seconds) {
  var previousVisibleArea = scrollingModel.visibleArea
  waitSuspending("Visible area is not changing", timeout,
                 getter = {
                   delay(15.seconds)
                   scrollingModel.visibleArea
                 },
                 checker = { currentVisibleArea ->
                   currentVisibleArea == previousVisibleArea.also { previousVisibleArea = currentVisibleArea }
                 }
  )
}

fun EditorImpl.assertNotVisible(visualPosition: VisualPosition) {
  assertThat(scrollingModel.visibleArea).satisfies({ !it.contains(visualPositionToXY(visualPosition)) })
}