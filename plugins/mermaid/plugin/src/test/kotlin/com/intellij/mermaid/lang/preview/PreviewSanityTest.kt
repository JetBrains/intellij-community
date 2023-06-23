package com.intellij.mermaid.lang.preview

import com.intellij.mermaid.preview.MermaidDiagramPreviewComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.ide.BuiltInServerManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@WithJcef
@TestApplication
class PreviewSanityTest {
  @JvmField
  @RegisterExtension
  val projectModel = ProjectModelExtension()

  @TestDisposable
  lateinit var disposable: Disposable

  private val project: Project
    get() = projectModel.project

  @Test
  fun `preview can actually load and dispose`() {
    BuiltInServerManager.getInstance().waitForStart()
    runBlocking(Dispatchers.EDT) {
      val preview = MermaidDiagramPreviewComponent(project)
      Disposer.register(disposable, preview)
      preview.load()
      preview.update("")
    }
  }
}
