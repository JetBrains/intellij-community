// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.markdown.preview

import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.MermaidPlugin
import com.intellij.mermaid.settings.MermaidSettingsConfigurable
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
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
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.annotations.ApiStatus

@OptIn(FlowPreview::class)
@ApiStatus.Internal
class MermaidPreviewEditor internal constructor(
  private val project: Project,
  file: VirtualFile
): FileEditor, UserDataHolder by UserDataHolderBase() {
  private val pluginScope
    get() = MermaidPlugin.coroutineScope(project)

  private val coroutineScope: CoroutineScope = pluginScope.childScope("MermaidPreviewEditorScope", CoroutineName("MermaidPreviewEditorScope"))
  private val updateRequests = MutableSharedFlow<String>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val reloadRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val document = FileDocumentManager.getInstance().getDocument(file)!!

  private val component by lazy { createComponent() }

  init {
    document.addDocumentListener(UpdatePreviewDocumentListener(), this)
    // EDT: one collector processes every request type, so browser ops never overlap (mermaid uses global state).
    // Each source is debounced on its own, so a reload is never coalesced away by a document update.
    coroutineScope.launch(context = Dispatchers.EDT) {
      merge(
        reloadRequests.debounce(100.milliseconds).map { PreviewRequest.Reload },
        updateRequests.debounce(20.milliseconds).map { PreviewRequest.Update(it) },
      ).collect { request ->
        try {
          val diagram = component.diagramComponent()
          when (request) {
            is PreviewRequest.Update -> diagram.update(request.text)
            PreviewRequest.Reload -> {
              diagram.load()
              diagram.update(document.text)
            }
          }
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Exception) {
          // A transient failure (e.g. browser navigating) must not kill the collector, or live updates would stop.
          thisLogger().warn("Failed to refresh the mermaid preview", e)
        }
      }
    }
    val connection = application.messageBus.connect(this)
    // Theme and stylesheet derive from the editor color scheme; reload on change.
    connection.subscribe(EditorColorsManager.TOPIC, EditorColorsListener {
      reloadRequests.tryEmit(Unit)
    })

    connection.subscribe(MermaidSettingsConfigurable.ChangeListener.TOPIC, MermaidSettingsConfigurable.ChangeListener {
      reloadRequests.tryEmit(Unit)
    })
  }

  private sealed interface PreviewRequest {
    data class Update(val text: String) : PreviewRequest
    data object Reload : PreviewRequest
  }

  private inner class UpdatePreviewDocumentListener: DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      updateRequests.tryEmit(event.document.text)
    }
  }

  private fun createComponent(): MermaidPreviewComponentContainer {
    return MermaidPreviewComponentContainer(
      parentDisposable = this,
      coroutineScope = coroutineScope,
      componentDeferred = createDiagramComponent(parentDisposable = this)
    )
  }

  private fun createDiagramComponent(parentDisposable: Disposable): Pair<MermaidDiagramPreviewComponent, Deferred<Unit>> {
    val component = MermaidDiagramPreviewComponent(project)
    Disposer.register(parentDisposable, component)
    return component to coroutineScope.async(context = Dispatchers.Default) {
      component.load()
      component.update(document.text)
    }
  }

  private class MermaidPreviewComponentContainer(
    parentDisposable: Disposable,
    coroutineScope: CoroutineScope,
    componentDeferred: Pair<MermaidDiagramPreviewComponent, Deferred<Unit>>
  ): JBLoadingPanel(BorderLayout(), parentDisposable) {
    private val loadDeferred = coroutineScope.async(context = Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
      startLoading()
      val (component, loadTask) = componentDeferred
      add(component)
      loadTask.await()
      stopLoading()
      return@async component
    }

    suspend fun diagramComponent(): MermaidDiagramPreviewComponent {
      return loadDeferred.await()
    }
  }

  override fun getComponent(): JComponent {
    return component
  }

  override fun getPreferredFocusedComponent(): JComponent {
    return component
  }

  override fun getName(): String {
    return MermaidBundle.message("mermaid.diagram.editor")
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
