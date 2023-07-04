package com.intellij.mermaid.preview

import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.isMermaidFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.readText
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.io.writeChild

internal class ExportDiagramAction: AnAction(MermaidBundle.message("action.Mermaid.ExportDiagram.text")), DumbAware {
  override fun actionPerformed(event: AnActionEvent) {
    val file = event.getRequiredData(CommonDataKeys.VIRTUAL_FILE)
    runBackgroundableTask(title = "Performing conversion", event.project, cancellable = true) {
      runBlockingCancellable {
        val diagramSource = file.readText()
        val content = performConversion(diagramSource)
        val directory = file.parent
        val result = directory.toNioPath().writeChild("${file.nameWithoutExtension}.svg", content)
        val file = VfsUtil.findFile(result, true)
        VfsUtil.markDirtyAndRefresh(false, false, true, file)
      }
    }
  }

  override fun update(event: AnActionEvent) {
    if (!Registry.`is`("mermaid.export.diagram.action.enable", false)) {
      event.presentation.isEnabledAndVisible = false
      return
    }
    if (!JBCefApp.isSupported()) {
      event.presentation.isEnabledAndVisible = false
      return
    }
    val file = event.getData(CommonDataKeys.VIRTUAL_FILE)
    event.presentation.isEnabledAndVisible = file?.isMermaidFile() == true
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}

private suspend fun loadDiagram(browser: JBCefBrowser, diagramSource: String) {
  val url = MermaidPreviewStaticServer.obtainStaticIndexUrl()
  browser.waitForPageLoad(url)
  val content = PreviewEncodingUtil.encodeContent(diagramSource)
  // language=JavaScript
  val code = """
  (function() {
    return new Promise(resolve => {
      window["updateMermaidDiagramContent"]("$content");
      resolve();
    });
  })();
  """.trimIndent()
  browser.executeCancellableJavaScript(code)
}

private suspend fun collectDiagramContent(browser: JBCefBrowser): String {
  // language=JavaScript
  val code = """
  (function() {
    return new Promise(resolve => resolve(window["collectDiagramContent"]()));
  })();
  """.trimIndent()
  return browser.executeCancellableJavaScript(code)!!
}

private suspend fun performConversion(diagramSource: String): String {
  return Disposer.newCheckedDisposable().use { disposable ->
    val browser = createBrowser()
    Disposer.register(disposable, browser)
    loadDiagram(browser, diagramSource)
    return@use collectDiagramContent(browser)
  }
}
