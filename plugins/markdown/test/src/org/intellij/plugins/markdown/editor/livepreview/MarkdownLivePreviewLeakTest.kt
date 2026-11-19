// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.markdown.frontend.editor.livepreview.MarkdownLivePreviewImageInlayRenderer
import com.intellij.markdown.frontend.editor.livepreview.MarkdownLivePreviewReconciler
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.runInEdtAndGet
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.io.path.writeText

@TestApplication
internal class MarkdownLivePreviewLeakTest {
  private val tempDir by tempPathFixture()

  @Test
  fun `a closed live preview leaks no editor, PSI or project`() {
    tempDir.resolve("test.md").writeText(CONTENT)
    ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB), "png", tempDir.resolve("image.png").toFile())
    val project = checkNotNull(ProjectManagerEx.getInstanceEx().openProject(tempDir, createTestOpenProjectOptions()))
    val psiFile = try {
      runInEdtAndGet { showAndCloseLivePreview(project) }
    }
    finally {
      PlatformTestUtil.forceCloseProjectWithoutSaving(project)
    }
    LeakHunter.checkLeak(LeakHunter.allRoots(), PsiFile::class.java) { it === psiFile }
    LeakHunter.checkLeak(LeakHunter.allRoots(), Project::class.java) { it === project }
  }

  private fun showAndCloseLivePreview(project: Project): PsiFile {
    val file = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempDir.resolve("test.md")))
    val psiFile = checkNotNull(PsiManager.getInstance(project).findFile(file))
    val editor = EditorFactory.getInstance().createEditor(psiFile.fileDocument, project, file, false)
    try {
      editor.caretModel.moveToOffset(CONTENT.length)
      editor.enableLivePreview()
      PlatformTestUtil.waitWithEventsDispatching(
        "Live preview did not show the heading and the image", { isShown(editor) }, 10
      )

      val shown = computeLivePreviewSpecs(psiFile, editor).elements.mapTo(HashSet()) { it.javaClass }
      val missing = MarkdownLivePreviewSpec::class.java.permittedSubclasses.filter { it !in shown }.map { it.simpleName }
      assertEquals(
        emptyList<String>(), missing,
        "Add an element of each missing spec type to CONTENT, so the test uses every renderer"
      )

      moveCaretTo(editor, CONTENT.indexOf("# Heading"))
      moveCaretTo(editor, CONTENT.length)
      MarkdownTestingUtil.paintEditor(editor)
    }
    finally {
      EditorFactory.getInstance().releaseEditor(editor)
    }
    LeakHunter.checkLeak(LeakHunter.allRoots(), EditorImpl::class.java) { it === editor }
    return psiFile
  }

  private fun isShown(editor: Editor): Boolean {
    val length = editor.document.textLength
    return MarkdownLivePreviewReconciler.getExisting(editor)?.hasCurrentSpecs() == true &&
           editor.foldingModel.allFoldRegions.any { it is CustomFoldRegion } &&
           editor.inlayModel.getBlockElementsInRange(0, length, MarkdownLivePreviewImageInlayRenderer::class.java).isNotEmpty()
  }

  private fun moveCaretTo(editor: Editor, offset: Int) {
    editor.caretModel.moveToOffset(offset)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  private companion object {
    val CONTENT = """
      before

      # Heading with **bold** and `code`

      > quote

      - [ ] task
        - nested

      -----

      ![alt](image.png)

      ```kotlin
      val x = 1
      ```

      after
    """.trimIndent()
  }
}
