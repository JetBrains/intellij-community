package com.intellij.mermaid.lang.preview

import com.intellij.idea.IJIgnore
import com.intellij.mermaid.markdown.preview.MermaidDiagramPreviewComponent
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension

@PreviewTest
@WithJcef
@ExtendWith(WaitForBuiltInServerExtension::class)
@TestApplication
@IJIgnore(issue = "IJPL-245868")
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
    HandlerLeakTest.ensureLoadHandlerIsNotLeaked(preview)
    ensureNoJcefObjectsLeaks(preview)
    ensureNoJcefObjectsLeaks(JBCefApp.getInstance())
  }

  companion object {
    fun ensureNoJcefObjectsLeaks(root: Any) {
      HandlerLeakTest.ensureJsQueryHandlerIsNotLeaked(root)
      LeakHunter.checkLeak(root, JBCefClient::class.java)
      LeakHunter.checkLeak(root, JBCefBrowser::class.java)
    }
  }
}
