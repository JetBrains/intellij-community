package org.intellij.plugins.markdown.preview.jcef

import com.intellij.idea.TestFor
import com.intellij.markdown.frontend.preview.MarkdownSplitEditorProvider
import com.intellij.markdown.jcef.preview.JCEFHtmlPanelProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.SkipInHeadlessEnvironment
import com.intellij.testFramework.dispatchAllEventsInIdeEventQueue
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.RunMethodInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.intellij.plugins.markdown.settings.MarkdownPreviewMode
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.intellij.plugins.markdown.ui.preview.MarkdownEditorWithPreview
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanelProvider
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewFileEditor
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import javax.swing.JComponent

@TestApplication
@SkipInHeadlessEnvironment
@RunInEdt(allMethods = false, writeIntent = true)
class MarkdownEditorCreationTest {
  @TestDisposable
  lateinit var disposable: Disposable

  private val project by projectFixture()

  @Test
  @RunMethodInEdt
  fun `editor provider accepts file without panel providers`() {
    ExtensionTestUtil.maskExtensions(MarkdownHtmlPanelProvider.EP_NAME, listOf(), disposable)
    val file = LightVirtualFile("some.md", "# Some")
    val provider = MarkdownSplitEditorProvider()
    assertThat(provider.accept(project, file)).isTrue()
  }

  @Test
  @RunMethodInEdt
  fun `opening editor without panel providers does not fail`() {
    ExtensionTestUtil.maskExtensions(MarkdownHtmlPanelProvider.EP_NAME, listOf(), disposable)
    val file = LightVirtualFile("some.md", "# Some")
    val manager = FileEditorManager.getInstance(project)
    manager.openFile(file)
    dispatchAllEventsInIdeEventQueue()
    manager.closeFile(file)
  }

  @Test
  @RunMethodInEdt
  fun `editor with jcef provider can be created`() {
    ExtensionTestUtil.maskExtensions(
      MarkdownHtmlPanelProvider.EP_NAME,
      listOf(JCEFHtmlPanelProvider()),
      disposable
    )
    val settings = MarkdownSettings.getInstance()
    val previewMode = settings.previewMode
    Disposer.register(disposable) { settings.update { it.previewMode = previewMode } }
    settings.update { it.previewMode = MarkdownPreviewMode.JCEF }
    val file = LightVirtualFile("some.md", "# Some")
    val provider = MarkdownSplitEditorProvider()
    file.putUserData(FileEditorProvider.KEY, provider)
    val manager = FileEditorManager.getInstance(project)
    manager.openFile(file)
    if (JCEFHtmlPanelProvider.canBeUsed()) {
      val editor = manager.getAllEditors(file).filterIsInstance<MarkdownEditorWithPreview>().single()
      // The preview creates no panel while the last used layout hides it.
      editor.setLayout(TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW)
      PlatformTestUtil.waitWithEventsDispatching(
        "The preview did not create a JCEF panel",
        { editor.previewEditor.getUserData(MarkdownPreviewFileEditor.PREVIEW_BROWSER)?.get() != null },
        JCEF_STARTUP_TIMEOUT_SECONDS
      )
    }
    dispatchAllEventsInIdeEventQueue()
    manager.closeFile(file)
  }

  @Disabled("It is not clear what should be a fallback if the preview provider failed.")
  @Test
  @TestFor(issues = ["IDEA-318146"])
  @RunMethodInEdt
  fun `opening editor with failing panel provider does not fail`() {
    ExtensionTestUtil.maskExtensions(
      MarkdownHtmlPanelProvider.EP_NAME,
      listOf(StubHtmlPanelProvider { FailingHtmlPanel() }),
      disposable
    )
    val file = LightVirtualFile("some.md", "# Some")
    val provider = MarkdownSplitEditorProvider()
    file.putUserData(FileEditorProvider.KEY, provider)
    val manager = FileEditorManager.getInstance(project)
    manager.openFile(file)
    dispatchAllEventsInIdeEventQueue()
    manager.closeFile(file)
  }

  private abstract class StubHtmlPanel: MarkdownHtmlPanel {
    override fun dispose() = Unit
    override fun setHtml(html: String, initialScrollOffset: Int, document: VirtualFile?) = Unit
    override fun reloadWithOffset(offset: Int) = Unit
    override fun scrollToMarkdownSrcOffset(offset: Int, smooth: Boolean) = Unit
    override fun addScrollListener(listener: MarkdownHtmlPanel.ScrollListener) = Unit
    override fun removeScrollListener(listener: MarkdownHtmlPanel.ScrollListener) = Unit
  }

  private class FailingHtmlPanel: StubHtmlPanel() {
    init {
      throw IllegalStateException("Should not be created")
    }

    override fun getComponent(): JComponent {
      throw NotImplementedError()
    }
  }

  private class StubHtmlPanelProvider(private val panel: () -> MarkdownHtmlPanel): MarkdownHtmlPanelProvider() {
    override fun createHtmlPanel(): MarkdownHtmlPanel {
      return panel.invoke()
    }

    override fun isAvailable(): AvailabilityInfo {
      return AvailabilityInfo.AVAILABLE
    }

    override fun getProviderInfo(): ProviderInfo {
      return ProviderInfo(
        "StubHtmlPanelProvider",
        StubHtmlPanelProvider::class.java.toString()
      )
    }
  }

  private companion object {
    const val JCEF_STARTUP_TIMEOUT_SECONDS = 60
  }
}
