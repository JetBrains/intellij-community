package com.intellij.mermaid.preview

import com.intellij.mermaid.MermaidPlugin
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
internal class MermaidPreviewEditor(
  private val project: Project,
  private val file: VirtualFile
): FileEditor, UserDataHolder by UserDataHolderBase() {
  private val pluginScope
    get() = service<MermaidPlugin>().coroutineScope

  private val coroutineScope = pluginScope.childScope(CoroutineName("MermaidPreviewEditorScope"))
  private val updateViewRequests = MutableSharedFlow<String>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val document = FileDocumentManager.getInstance().getDocument(file)!!

  private val component by lazy { createComponent() }

  init {
    document.addDocumentListener(UpdatePreviewDocumentListener(), this)
    coroutineScope.launch(context = Dispatchers.EDT) {
      // debounce to prevent JBCefQuery pool exhaustion
      updateViewRequests.debounce(20.milliseconds).collectLatest {
        component.update(it)
      }
    }
  }

  private inner class UpdatePreviewDocumentListener: DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      updateViewRequests.tryEmit(event.document.text)
    }
  }

  private fun createComponent(): MermaidDiagramPreviewComponent {
    val component = MermaidDiagramPreviewComponent()
    Disposer.register(this, component)
    runBlocking {
      coroutineScope.launch(context = Dispatchers.Default) {
        component.load()
        component.update(document.text)
      }
    }
    return component
  }

  override fun getComponent(): JComponent {
    return component
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return component
  }

  override fun getName(): String {
    return "Mermaid Diagram Editor"
  }

  override fun setState(state: FileEditorState) = Unit

  override fun isModified(): Boolean {
    return false
  }

  override fun isValid(): Boolean {
    return true
  }

  override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

  override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

  override fun dispose() {
    coroutineScope.cancel("Cancel on dispose")
  }
}
