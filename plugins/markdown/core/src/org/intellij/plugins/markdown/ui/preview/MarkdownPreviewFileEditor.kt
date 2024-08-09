// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.CommonBundle
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.StartupUiUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.settings.MarkdownExtensionsSettings
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil.generateMarkdownHtml
import org.intellij.plugins.markdown.ui.preview.jcef.MarkdownJCEFHtmlPanel
import org.intellij.plugins.markdown.util.MarkdownPluginScope
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.AWTEvent
import java.awt.BorderLayout
import java.awt.Point
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseEvent
import java.beans.PropertyChangeListener
import java.lang.ref.WeakReference
import javax.swing.JComponent
import javax.swing.JPanel


@Internal
class MarkdownPreviewFileEditor(
  private val project: Project,
  private val file: VirtualFile,
  private val document: Document,
) : UserDataHolder by UserDataHolderBase(), FileEditor {
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

    StartupUiUtil.addAwtListener(AWTEvent.MOUSE_EVENT_MASK, this) { event ->
      if (event is MouseEvent && event.id == MouseEvent.MOUSE_CLICKED && event.button == MouseEvent.BUTTON3
          && event.component.isShowing() && htmlPanelWrapper.isShowing()
          && component.containsScreenLocation(event.locationOnScreen)
      ) {
        val context = SimpleDataContext.builder()
          .setParent(DataManager.getInstance().getDataContext(event.component))
          .add(PREVIEW_POPUP_POINT, RelativePoint.fromScreen(event.locationOnScreen))
          .build()
        val group = requireNotNull(ActionUtil.getActionGroup("Markdown.PreviewGroup"))
        val popup = JBPopupFactory.getInstance().createActionGroupPopup(
          null,
          group,
          context,
          JBPopupFactory.ActionSelectionAid.MNEMONICS,
          true
        )
        popup.showInScreenCoordinates(event.component, event.locationOnScreen)
      }
    }
  }

  private fun JComponent.containsScreenLocation(screenLocation: Point): Boolean {
    val relativeX = screenLocation.x - locationOnScreen.x
    val relativeY = screenLocation.y - locationOnScreen.y
    return (relativeX >= 0 && relativeX < size.width && relativeY >= 0 && relativeY < size.height)
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
    writeIntentReadAction {
      val offset = editor.caretModel.offset
      panel.setHtml(lastRenderedHtml, offset, file)
    }
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

    internal val PREVIEW_POPUP_POINT: DataKey<RelativePoint> = DataKey.create("PREVIEW_POPUP_POINT")
    internal val PREVIEW_JCEF_PANEL: DataKey<WeakReference<MarkdownJCEFHtmlPanel>> = DataKey.create("PREVIEW_JCEF_PANEL")
  }
}
