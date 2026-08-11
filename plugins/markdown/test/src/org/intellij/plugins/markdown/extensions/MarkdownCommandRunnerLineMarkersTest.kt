// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.execution.Executor
import com.intellij.icons.AllIcons
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownRunner
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.event.MouseEvent

@RunWith(JUnit4::class)
class MarkdownCommandRunnerLineMarkersTest : BasePlatformTestCase() {

  private val markdownRunnerEp = ExtensionPointName.create<MarkdownRunner>("org.intellij.markdown.markdownRunner")
  private val capturingRunner = CapturingRunner()

  override fun setUp() {
    super.setUp()
    ExtensionTestUtil.maskExtensions(markdownRunnerEp, listOf(capturingRunner), testRootDisposable)
    val testMdFile = myFixture.addFileToProject("foo/test.md", "```shell\npwd\n```")
    myFixture.openFileInEditor(testMdFile.virtualFile)
  }

  override fun tearDown() {
    try {
      MarkdownSettings.getInstance(project).useFileDirectoryForCommands = null
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  @Test
  fun `block run marker is shown for shell code fence`() {
    myFixture.doHighlighting()
    val markers = DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, project)
    assertNotNull(markers.firstOrNull { it.icon == AllIcons.RunConfigurations.TestState.Run_run })
  }

  @Test
  fun `block run marker invokes runner with base directory as working directory`() {
    MarkdownSettings.getInstance(project).useFileDirectoryForCommands = false
    fireBlockMarkerAction()
    val expected = BaseProjectDirectories.getInstance(project).getBaseDirectoryFor(myFixture.file.virtualFile)?.canonicalPath
    assertEquals(expected, capturingRunner.capturedDir)
  }

  @Test
  fun `block run marker invokes runner with file directory as working directory when setting is enabled`() {
    MarkdownSettings.getInstance(project).useFileDirectoryForCommands = true
    fireBlockMarkerAction()
    assertEquals(myFixture.file.virtualFile.parent.canonicalPath, capturingRunner.capturedDir)
  }

  @Test
  fun `block run marker strips trailing hash comment from command`() {
    MarkdownSettings.getInstance(project).useFileDirectoryForCommands = true
    val testMdFile = myFixture.addFileToProject(
      "foo/withComment.md",
      "```bash\nnpm run dev       # start Vite dev server (HMR, localhost:5173)\n```"
    )
    myFixture.openFileInEditor(testMdFile.virtualFile)
    fireBlockMarkerAction()
    val command = capturingRunner.capturedCommand
    assertNotNull("Runner was not invoked", command)
    assertEquals("npm run dev", command!!.trim())
  }

  private fun fireBlockMarkerAction() {
    myFixture.doHighlighting()
    val markers = DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, project)
    val blockMarker = markers.first { it.icon == AllIcons.RunConfigurations.TestState.Run_run }
    val action = (blockMarker.createGutterRenderer() as GutterIconRenderer).clickAction!!
    val dataContext = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).build()
    val inputEvent = MouseEvent(myFixture.editor.component, MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, false)
    val event = AnActionEvent.createEvent(dataContext, action.templatePresentation.clone(), ActionPlaces.EDITOR_GUTTER, ActionUiKind.NONE, inputEvent)
    action.actionPerformed(event)
  }

  private class CapturingRunner : MarkdownRunner {
    var capturedDir: String? = "not-captured"
    var capturedCommand: String? = null

    override fun isApplicable(language: Language?) = true

    override fun run(command: String, project: Project, workingDirectory: String?, executor: Executor): Boolean {
      capturedDir = workingDirectory
      capturedCommand = command
      return true
    }

    override fun title() = "Test Runner"
  }
}
