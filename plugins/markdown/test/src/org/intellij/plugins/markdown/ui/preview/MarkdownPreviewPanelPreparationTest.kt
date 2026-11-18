// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.replaceService
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.intellij.plugins.markdown.util.MarkdownPluginScope
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.JComponent
import javax.swing.JPanel

@TestApplication
@TestFor(issues = ["IJPL-261473"])
internal class MarkdownPreviewPanelPreparationTest {
  companion object {
    private val projectFixture = projectFixture()

    private const val PREPARE_IN_BACKGROUND = "prepare in background"
    private const val PREPARE_ON_EDT = "prepare on EDT"
    private const val CREATE = "create"
    private const val DISPOSE = "dispose"
  }

  @TestDisposable
  lateinit var disposable: Disposable

  private val panelProvider = RecordingHtmlPanelProvider()

  @BeforeEach
  fun maskPanelProviders() {
    ExtensionTestUtil.maskExtensions(MarkdownHtmlPanelProvider.EP_NAME, listOf(panelProvider), disposable)
  }

  @Test
  fun `the first panel is created after the provider is prepared in background`(): Unit = timeoutRunBlocking {
    withPreviewEditor {
      assertThat(panelProvider.awaitCreatedPanels(1)).containsExactly(PREPARE_IN_BACKGROUND, CREATE)
    }
  }

  @Test
  fun `a preview that is shown again creates its panel after the provider is prepared in background`(): Unit = timeoutRunBlocking {
    withPreviewEditor { preview ->
      panelProvider.awaitCreatedPanels(1)
      withContext(Dispatchers.UI) {
        preview.component.isVisible = false
        preview.component.isVisible = true
      }
      assertThat(panelProvider.awaitCreatedPanels(2))
        .containsExactly(PREPARE_IN_BACKGROUND, CREATE, DISPOSE, PREPARE_IN_BACKGROUND, CREATE)
    }
  }

  @Test
  fun `a hidden preview creates no panel until it is shown`(): Unit = timeoutRunBlocking {
    val pluginScope = this + Dispatchers.Default
    projectFixture.get().replaceService(MarkdownPluginScope::class.java, MarkdownPluginScope(pluginScope), disposable)
    withPreviewEditor(isPreviewVisible = false) { preview ->
      awaitPreviewCoroutines(pluginScope)
      assertThat(panelProvider.recordedCalls).containsExactly(PREPARE_IN_BACKGROUND)
      withContext(Dispatchers.UI) {
        preview.component.isVisible = true
      }
      assertThat(panelProvider.awaitCreatedPanels(1)).containsExactly(PREPARE_IN_BACKGROUND, PREPARE_IN_BACKGROUND, CREATE)
    }
  }

  /**
   * Runs [action] with a preview of a plain main editor.
   * The test does not use [MarkdownEditorWithPreview], because its layout can hide the preview at any time.
   */
  private suspend fun withPreviewEditor(isPreviewVisible: Boolean = true, action: suspend (MarkdownPreviewFileEditor) -> Unit) {
    val project = projectFixture.get()
    val [preview, mainEditor] = withContext(Dispatchers.EDT) {
      val file = LightVirtualFile("test.md", "# Heading\n")
      val document = FileDocumentManager.getInstance().getDocument(file)!!
      val preview = MarkdownPreviewFileEditor(project, file, document)
      preview.component.isVisible = isPreviewVisible
      val mainEditor = EditorFactory.getInstance().createEditor(document, project)
      preview.setMainEditor(mainEditor)
      preview to mainEditor
    }
    try {
      action(preview)
    }
    finally {
      withContext(Dispatchers.EDT + NonCancellable) {
        Disposer.dispose(preview)
        EditorFactory.getInstance().releaseEditor(mainEditor)
      }
    }
  }

  private suspend fun awaitPreviewCoroutines(pluginScope: CoroutineScope) {
    pluginScope.coroutineContext.job.children.flatMap { previewScope -> previewScope.children }.toList().joinAll()
  }

  private class RecordingHtmlPanelProvider : MarkdownHtmlPanelProvider() {
    private val calls = MutableStateFlow(emptyList<String>())

    val recordedCalls: List<String>
      get() = calls.value

    suspend fun awaitCreatedPanels(count: Int): List<String> {
      return calls.first { calls -> calls.count { it == CREATE } >= count }
    }

    override fun prepare() {
      record(if (EDT.isCurrentThreadEdt()) PREPARE_ON_EDT else PREPARE_IN_BACKGROUND)
    }

    override fun createHtmlPanel(): MarkdownHtmlPanel {
      record(CREATE)
      return StubHtmlPanel { record(DISPOSE) }
    }

    override fun isAvailable(): AvailabilityInfo = AvailabilityInfo.AVAILABLE

    override fun getProviderInfo(): ProviderInfo = ProviderInfo("Recording", RecordingHtmlPanelProvider::class.java.name)

    private fun record(call: String) {
      calls.update { it + call }
    }
  }

  private class StubHtmlPanel(private val onDispose: () -> Unit) : MarkdownHtmlPanel {
    private val component = JPanel()

    override fun getComponent(): JComponent = component
    override fun setHtml(html: String, initialScrollOffset: Int, document: VirtualFile?) = Unit
    override fun reloadWithOffset(offset: Int) = Unit
    override fun addScrollListener(listener: MarkdownHtmlPanel.ScrollListener) = Unit
    override fun removeScrollListener(listener: MarkdownHtmlPanel.ScrollListener) = Unit
    override fun dispose() = onDispose()
  }
}
