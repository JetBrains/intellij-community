// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.markdown.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.ui.components.BorderLayoutPanel
import org.intellij.plugins.markdown.ui.preview.accessor.MarkdownLinkOpener

class MermaidDiagramPreviewComponent(private val project: Project): BorderLayoutPanel(), Disposable {
  val browser: JBCefBrowser by disposableHolder(createBrowser())
  private val openLinkQuery by disposableHolder(JBCefJSQuery.create(browser as JBCefBrowserBase))

  init {
    Disposer.register(this, openLinkQuery)
    Disposer.register(this, browser)
    addToCenter(browser.component)
    openLinkQuery.addVoidHandler { link ->
      link?.let(::openLink)
    }
  }

  suspend fun load() {
    val url = MermaidPreviewStaticServer.obtainStaticIndexUrl()
    with(browser) {
      waitForPageLoad(url)
      injectLinkOpener()
    }
  }

  internal class PreviewUpdateException(cause: Exception): IllegalStateException(cause)

  suspend fun update(text: String) {
    val content = PreviewEncodingUtil.encodeContent(text)
    // language=JavaScript
    val code = """
    (function() {
      return new Promise(resolve => {
        window["updateMermaidDiagramContent"]("$content");
        resolve();
      });
    })();
    """.trimIndent()
    try {
      browser.executeCancellableJavaScript(code)
    } catch (exception: JsCallExecutionException) {
      throw PreviewUpdateException(exception)
    }
  }

  override fun dispose() {
    removeAll()
  }

  private suspend fun injectLinkOpener() {
    // language=JavaScript
    val code = """
    (function() {
      return new Promise(resolve => {
        window["openLink"] = function(link) {
          window.${openLinkQuery.funcName}({
            request: "" + link,
            onSuccess: (response) => {}, 
            onFailure: (errorCode, errorMessage) => {}
          });
        };
        resolve();
      });
    })();
    """.trimIndent()
    browser.executeCancellableJavaScript(code)
  }

  private fun openLink(link: String) {
    @Suppress("UnstableApiUsage")
    MarkdownLinkOpener.getInstance().openLink(project, link)
  }
}