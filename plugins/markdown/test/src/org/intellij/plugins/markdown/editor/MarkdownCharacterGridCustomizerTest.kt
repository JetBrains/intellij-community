package org.intellij.plugins.markdown.editor

import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownCharacterGridCustomizerTest {
  private val projectFixture = projectFixture()
  private val fileEditorManagerFixture = projectFixture.fileEditorManagerFixture()

  @Test
  fun `grid mode activates for file with CJK and table`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val editor = openMarkdownFile(
      """
      | one | 二 |
      |-----|----|
      | a   | b  |
      """.trimIndent()
    )
    awaitGridMode(editor, expectActive = true)
    assertThat(editor.settings.characterGridWidthMultiplier).isEqualTo(1.0f)
    assertThat(editor.characterGrid).isNotNull
  }

  @Test
  fun `grid mode stays off for file with table but no CJK`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val editor = openMarkdownFile(
      """
      | one | two |
      |-----|-----|
      | a   | b   |
      """.trimIndent()
    )
    delay(500)
    assertThat(editor.settings.characterGridWidthMultiplier).isNull()
    assertThat(editor.characterGrid).isNull()
  }

  @Test
  fun `grid mode stays off for CJK prose without table`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val editor = openMarkdownFile("Some plain text with 中文 sprinkled in.\n")
    delay(500)
    assertThat(editor.settings.characterGridWidthMultiplier).isNull()
    assertThat(editor.characterGrid).isNull()
  }

  private fun openMarkdownFile(content: String): EditorImpl {
    val file = LightVirtualFile("test.md", content)
    val manager = fileEditorManagerFixture.get()
    val editors = manager.openFile(file, true)
    return (editors.first() as TextEditor).editor as EditorImpl
  }

  private suspend fun awaitGridMode(editor: EditorImpl, expectActive: Boolean) {
    withTimeoutOrNull(5_000) {
      while ((editor.settings.characterGridWidthMultiplier != null) != expectActive) delay(50)
    } ?: error(
      "Grid mode ${if (expectActive) "did not activate" else "stayed active unexpectedly"} within 5s"
    )
  }
}
