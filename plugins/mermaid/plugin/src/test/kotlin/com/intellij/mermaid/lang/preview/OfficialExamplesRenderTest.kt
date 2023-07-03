package com.intellij.mermaid.lang.preview

import com.intellij.mermaid.preview.MermaidDiagramPreviewComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.util.io.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path

@PreviewTest
@WithJcef
@ExtendWith(WaitForBuiltInServerExtension::class)
@TestApplication
class OfficialExamplesRenderTest {
  @JvmField
  @RegisterExtension
  val projectModel = ProjectModelExtension()

  @TestDisposable
  lateinit var disposable: Disposable

  private val project: Project
    get() = projectModel.project

  @TestFactory
  fun generateExampleTests(): List<DynamicNode> {
    OfficialExamplesTestData.assumeAvailable()
    return OfficialExamplesTestData.generateDynamicTests(
      testDataPath = OfficialExamplesTestData.testDataPath,
      test = ::testDiagram
    )
  }

  private fun testDiagram(path: Path) {
    val diagramContent = path.readText()
    runBlocking(Dispatchers.EDT) {
      val preview = MermaidDiagramPreviewComponent(project)
      Disposer.register(disposable, preview)
      preview.load()
      preview.update(diagramContent)
    }
  }
}
