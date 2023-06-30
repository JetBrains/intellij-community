package com.intellij.mermaid.lang.preview

import com.intellij.mermaid.preview.MermaidDiagramPreviewComponent
import com.intellij.mermaid.preview.WaitForLoadHandlerAdapter
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefClient
import com.intellij.ui.jcef.JBCefJSQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.cef.handler.CefLoadHandler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension

@PreviewTest
@WithJcef
@ExtendWith(WaitForBuiltInServerExtension::class)
@TestApplication
class PreviewHandlerLeakTest {
  @JvmField
  @RegisterExtension
  val projectModel = ProjectModelExtension()

  private val project: Project
    get() = projectModel.project

  @Test
  fun `no leaks after dispose`() {
    val preview = Disposer.newCheckedDisposable().use { disposable ->
      return@use runBlocking(Dispatchers.EDT) {
        val preview = MermaidDiagramPreviewComponent(project)
        Disposer.register(disposable, preview)
        preview.load()
        preview.update("")
        return@runBlocking preview
      }
    }
    LeakHunter.checkLeak(preview, CefLoadHandler::class.java) { it is WaitForLoadHandlerAdapter }
    ensureNoJcefObjectsLeaks(preview)
    ensureNoJcefObjectsLeaks(JBCefApp.getInstance())
  }

  private fun ensureNoJcefObjectsLeaks(root: Any) {
    LeakHunter.checkLeak(root, JBCefJSQuery::class.java)
    LeakHunter.checkLeak(root, JBCefClient::class.java)
    LeakHunter.checkLeak(root, JBCefBrowser::class.java)
  }
}
