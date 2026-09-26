// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.markdown.preview

import com.intellij.mermaid.MermaidBundle.message
import com.intellij.mermaid.MermaidNotifications
import com.intellij.mermaid.lang.isMermaidFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.io.write

internal class ExportDiagramAction: AnAction(), DumbAware {
  override fun actionPerformed(event: AnActionEvent) {
    val file = event.getRequiredData(CommonDataKeys.VIRTUAL_FILE)
    runBackgroundableTask(
      title = message("export.diagram.task.title", file.name),
      project = event.project,
      cancellable = true
    ) {
      runBlockingCancellable {
        val diagramSource = readAction {
          FileDocumentManager.getInstance().getDocument(file)?.text.orEmpty()
        }
        if (diagramSource.isEmpty()) {
          MermaidNotifications.showWarning(
            event.project,
            id = "mermaid.empty.file.to.export",
            title = message("mermaid.notification.empty.file.to.export.title"),
            message = message("mermaid.notification.empty.file.to.export.message")
          )
          return@runBlockingCancellable
        }
        val content = performConversion(diagramSource)
        val directory = file.parent
        val result = directory.toNioPath().resolve("${file.nameWithoutExtension}.svg").write(content)
        val svgFile = VfsUtil.findFile(result, true)
        VfsUtil.markDirtyAndRefresh(false, false, true, svgFile)
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

private const val DefaultMermaidCliViewportWidth = 800
private const val DefaultMermaidCliViewportHeight = 600

private suspend fun forceDimensions(browser: JBCefBrowser, width: Int, height: Int) {
  // language=JavaScript
  val code = """
  (function () {
    const style = `@page {
      size: ${width}px ${height}px;
    }`;
    const element = window.document.createElement("style");
    element.innerHTML += style;
    window.document.head.appendChild(element);
    const body = window.document.body;
    body.style.width = "${width}px";
    body.style.height = "${height}px";
    return Promise.resolve();
  })();
  """.trimIndent()
  browser.executeCancellableJavaScript(code)
}

private suspend fun loadDiagram(browser: JBCefBrowser, diagramSource: String) {
  val url = MermaidPreviewStaticServer.obtainStaticIndexUrl()
  browser.waitForPageLoad(url)
  // It turns out that some diagrams (namely pie chart) have their dimensions hardcoded.
  // It seems that such hardcoded dimensions were only tested with the default viewport dimensions
  // provided by the Puppeteer (800x600).
  // So, to fix most of the issues with positioning, we have to explicitly set the page dimensions
  // to match the defaults from the Puppeteer.
  forceDimensions(
    browser,
    width = DefaultMermaidCliViewportWidth,
    height = DefaultMermaidCliViewportHeight
  )
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