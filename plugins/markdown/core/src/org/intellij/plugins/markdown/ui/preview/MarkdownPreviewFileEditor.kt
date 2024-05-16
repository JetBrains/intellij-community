// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.CommonBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.settings.MarkdownExtensionsSettings
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil.generateMarkdownHtml
import org.intellij.plugins.markdown.util.MarkdownPluginScope
import java.awt.BorderLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.beans.PropertyChangeListener
import java.lang.ref.WeakReference
import javax.swing.JComponent
import javax.swing.JPanel

class MarkdownPreviewFileEditor(private val project: Project, private val file: VirtualFile) : UserDataHolder by UserDataHolderBase(), FileEditor {
  private val document = checkNotNull(FileDocumentManager.getInstance().getDocument(file))

  private val htmlPanelWrapper: JPanel = JPanel(BorderLayout()).apply { addComponentListener(AttachPanelOnVisibilityChangeListener()) }
  private var panel: MarkdownHtmlPanel? = null
  var lastPanelProviderInfo: MarkdownHtmlPanelProvider.ProviderInfo? = null
    private set

  private var lastRenderedHtml = ""

  private var mainEditor = MutableStateFlow<Editor?>(null)

  private var isDisposed: Boolean = false

  private val coroutineScope = MarkdownPluginScope.createChildScope(project)

  init {
    document.addDocumentListener(ReparseContentDocumentListener(), this)

    coroutineScope.launch(Dispatchers.EDT) { attachHtmlPanel() }

    val messageBusConnection = project.messageBus.connect(this)
    val settingsChangedListener = UpdatePanelOnSettingsChangedListener()
    messageBusConnection.subscribe(MarkdownSettings.ChangeListener.TOPIC, settingsChangedListener)
    messageBusConnection.subscribe(
      MarkdownExtensionsSettings.ChangeListener.TOPIC,
      MarkdownExtensionsSettings.ChangeListener { fromSettingsDialog: Boolean ->
        if (!fromSettingsDialog) {
          coroutineScope.launch(Dispatchers.EDT) {
            val editor = mainEditor.firstOrNull() ?: return@launch
            val offset = editor.caretModel.offset
            panel?.reloadWithOffset(offset)
          }
        }
      })
  }

  private suspend fun setupScrollHelper() {
    val editor = mainEditor.firstOrNull() ?: return
    val actualEditor = editor as? EditorImpl ?: return
    val scrollPane = actualEditor.scrollPane
    val helper = PreciseVerticalScrollHelper(actualEditor) { (panel as? MarkdownHtmlPanelEx) }
    scrollPane.addMouseWheelListener(helper)
    Disposer.register(this@MarkdownPreviewFileEditor) { scrollPane.removeMouseWheelListener(helper) }
  }

  fun setMainEditor(editor: Editor) {
    check(mainEditor.value == null)
    mainEditor.value = editor
    if (Registry.`is`("markdown.experimental.boundary.precise.scroll.enable")) {
      coroutineScope.launch { setupScrollHelper() }
    }
  }

  fun scrollToSrcOffset(offset: Int) {
    panel?.scrollToMarkdownSrcOffset(offset, true)
  }

  override fun getComponent(): JComponent {
    return htmlPanelWrapper
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return panel?.component
  }

  override fun getName(): String {
    return MarkdownBundle.message("markdown.editor.preview.name")
  }

  override fun setState(state: FileEditorState) {}

  override fun isModified(): Boolean {
    return false
  }

  override fun isValid(): Boolean {
    return true
  }

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

  override fun getFile(): VirtualFile {
    return file
  }

  override fun dispose() {
    if (panel != null) {
      detachHtmlPanel()
    }
    isDisposed = true
    coroutineScope.cancel()
  }

  private fun retrievePanelProvider(settings: MarkdownSettings): MarkdownHtmlPanelProvider {
    val providerInfo = settings.previewPanelProviderInfo
    var provider = MarkdownHtmlPanelProvider.createFromInfo(providerInfo)
    if (provider.isAvailable() !== MarkdownHtmlPanelProvider.AvailabilityInfo.AVAILABLE) {
      val defaultProvider = MarkdownHtmlPanelProvider.createFromInfo(MarkdownSettings.defaultProviderInfo)
      Messages.showMessageDialog(
        htmlPanelWrapper,
        MarkdownBundle.message("dialog.message.tried.to.use.preview.panel.provider", providerInfo.name),
        CommonBundle.getErrorTitle(),
        Messages.getErrorIcon()
      )
      MarkdownSettings.getInstance(project).previewPanelProviderInfo = defaultProvider.providerInfo
      provider = MarkdownHtmlPanelProvider.getProviders().find { p: MarkdownHtmlPanelProvider -> p.isAvailable() === MarkdownHtmlPanelProvider.AvailabilityInfo.AVAILABLE }!!
    }
    lastPanelProviderInfo = settings.previewPanelProviderInfo
    return provider
  }

  @RequiresEdt
  private suspend fun updateHtml() {
    val panel = this.panel ?: return
    if (!file.isValid || isDisposed) {
      return
    }

    val html = readAction { generateMarkdownHtml(file, document.text, project) }

    val currentHtml = "<html><head></head>$html</html>"
    lastRenderedHtml = currentHtml
    val editor = mainEditor.firstOrNull() ?: return
    val offset = editor.caretModel.offset
    panel.setHtml(lastRenderedHtml, offset, file)
  }

  @RequiresEdt
  private fun detachHtmlPanel() {
    val panel = this.panel
    if (panel != null) {
      htmlPanelWrapper.remove(panel.component)
      Disposer.dispose(panel)
      this.panel = null
    }
    putUserData(PREVIEW_BROWSER, null)
  }

  @RequiresEdt
  private suspend fun attachHtmlPanel() {
    val settings = MarkdownSettings.getInstance(project)
    val panel = retrievePanelProvider(settings).createHtmlPanel(project, file)
    this.panel = panel
    htmlPanelWrapper.add(panel.component, BorderLayout.CENTER)
    if (htmlPanelWrapper.isShowing) htmlPanelWrapper.validate()
    htmlPanelWrapper.repaint()
    lastRenderedHtml = ""
    putUserData(PREVIEW_BROWSER, WeakReference(panel))
    updateHtml()
  }

  private inner class ReparseContentDocumentListener : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      coroutineScope.launch(Dispatchers.EDT) { updateHtml() }
    }
  }

  private inner class AttachPanelOnVisibilityChangeListener : ComponentAdapter() {
    override fun componentShown(event: ComponentEvent) {
      if (panel == null) {
        coroutineScope.launch(Dispatchers.EDT) { attachHtmlPanel() }
      }
    }

    override fun componentHidden(event: ComponentEvent) {
      if (panel != null) {
        detachHtmlPanel()
      }
    }
  }

  private inner class UpdatePanelOnSettingsChangedListener : MarkdownSettings.ChangeListener {
    override fun beforeSettingsChanged(settings: MarkdownSettings) {}

    override fun settingsChanged(settings: MarkdownSettings) {
      coroutineScope.launch(Dispatchers.EDT) {
        if (settings.splitLayout != TextEditorWithPreview.Layout.SHOW_EDITOR) {
          if (panel == null) {
            attachHtmlPanel()
          }
          else if (lastPanelProviderInfo == null ||
                   MarkdownHtmlPanelProvider.createFromInfo(lastPanelProviderInfo!!) == retrievePanelProvider(settings)) {
            detachHtmlPanel()
            attachHtmlPanel()
          }
        }
      }
    }
  }

  companion object {
    val PREVIEW_BROWSER: Key<WeakReference<MarkdownHtmlPanel>> = Key.create("PREVIEW_BROWSER")
  }
}
