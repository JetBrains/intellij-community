// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.editor

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFilePropertyEvent
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

private val LOG = logger<ResourceBundleEditorFileListener>()

private val FORCE_UPDATE: Update = object : Update("FORCE_UPDATE") {
  override fun run() {
    throw IllegalStateException()
  }
}

internal class ResourceBundleEditorFileListener(private val editor: ResourceBundleEditor) : VirtualFileListener {
  private val project = editor.resourceBundle.project
  private val eventProcessor = MyVfsEventsProcessor()

  fun flush() {
    FileDocumentManager.getInstance().saveAllDocuments()
    eventProcessor.flush()
  }

  override fun fileCreated(event: VirtualFileEvent) {
    eventProcessor.queue(event, EventType.FILE_CREATED)
  }

  override fun fileDeleted(event: VirtualFileEvent) {
    eventProcessor.queue(event, EventType.FILE_DELETED)
  }

  override fun propertyChanged(event: VirtualFilePropertyEvent) {
    eventProcessor.queue(event, EventType.PROPERTY_CHANGED)
  }

  override fun contentsChanged(event: VirtualFileEvent) {
    eventProcessor.queue(event, EventType.CONTENT_CHANGED)
  }

  private inner class MyVfsEventsProcessor {
    private val eventQueue = AtomicReference(ConcurrentCollectionFactory.createConcurrentSet<EventWithType>())
    private val updateQueue = MyMergingUpdateQueue(project = project, editor = editor, eventQueue = eventQueue)

    fun queue(event: VirtualFileEvent, type: EventType) {
      eventQueue.updateAndGet {
        it.add(EventWithType(type, event))
        it
      }
      updateQueue.queue(FORCE_UPDATE)
    }

    fun flush() {
      updateQueue.flush()
    }
  }
}

private enum class EventType {
  FILE_CREATED,
  FILE_DELETED,
  CONTENT_CHANGED, PROPERTY_CHANGED
}

private class SetViewerPropertyRunnable(private val editor: ResourceBundleEditor) : Runnable {
  private val files = ArrayList<VirtualFile>()
  private val isViewer = ArrayList<Boolean>()

  fun addFile(virtualFile: VirtualFile, isViewer: Boolean) {
    files.add(virtualFile)
    this.isViewer.add(isViewer)
  }

  override fun run() {
    for ((i, file) in files.withIndex()) {
      val viewer = isViewer[i]
      val editor = editor.translationEditors[file]
      if (editor != null) {
        editor.isViewer = viewer
      }
    }
  }
}

private class MyMergingUpdateQueue(
  private val project: Project,
  private val editor: ResourceBundleEditor,
  private val eventQueue: AtomicReference<MutableSet<EventWithType>>,
) : MergingUpdateQueue(
  name = "rbe.vfs.listener.queue",
  mergingTimeSpan = 200,
  isActive = true,
  modalityStateComponent = editor.component,
  parent = editor,
  activationComponent = editor.component,
  executeInDispatchThread = false,
) {
  @Suppress("DuplicatedCode")
  override suspend fun executeUpdates(updates: List<Update>) {
    val events: Set<EventWithType> = eventQueue.getAndSet(ConcurrentCollectionFactory.createConcurrentSet())

    val toDo = readAction {
      computeInReadAction(events)
    } ?: return

    withContext(Dispatchers.EDT) {
      if (editor.isValid) {
        toDo.run()
      }
    }
  }

  private fun computeInReadAction(events: Set<EventWithType>): Runnable? {
    if (!editor.isValid) {
      return null
    }

    var toDo: Runnable? = null
    val resourceBundleAsSet by lazy {
      editor.resourceBundle.propertiesFiles.mapTo(HashSet()) { it.virtualFile }
    }

    for (e in events) {
      if (e.type == EventType.FILE_DELETED || (e.type == EventType.PROPERTY_CHANGED && e.getPropertyName() == VirtualFile.PROP_NAME)) {
        if (editor.translationEditors.containsKey(e.file)) {
          var validFilesCount = 0
          val bundle = editor.resourceBundle
          if (bundle.isValid) {
            for (file in bundle.propertiesFiles) {
              if (file.containingFile.isValid) {
                validFilesCount++
              }
              if (validFilesCount == 2) {
                break
              }
            }
          }

          toDo = if (validFilesCount > 1) {
            Runnable { editor.recreateEditorsPanel() }
          }
          else {
            Runnable { FileEditorManager.getInstance(project).closeFile(editor.file) }
          }
          break
        }
        else if (resourceBundleAsSet.contains(e.file)) {
          // new file in the bundle
          toDo = Runnable { editor.recreateEditorsPanel() }
          break
        }
      }
      else if (e.type == EventType.FILE_CREATED) {
        if (resourceBundleAsSet.contains(e.file)) {
          toDo = Runnable { editor.recreateEditorsPanel() }
          break
        }
      }
      else if (e.type == EventType.PROPERTY_CHANGED && e.getPropertyName() == VirtualFile.PROP_WRITABLE) {
        if (editor.translationEditors.containsKey(e.file)) {
          if (toDo == null) {
            toDo = SetViewerPropertyRunnable(editor)
          }

          if (toDo is SetViewerPropertyRunnable) {
            toDo.addFile(virtualFile = e.file, isViewer = !(e.getPropertyNewValue() as Boolean))
          }
          else {
            toDo = Runnable { editor.recreateEditorsPanel() }
            break
          }
        }
      }
      else {
        if (editor.translationEditors.containsKey(e.file)) {
          if (toDo is SetViewerPropertyRunnable) {
            toDo = Runnable { editor.recreateEditorsPanel() }
            break
          }
          else if (toDo == null) {
            toDo = Runnable { editor.updateEditorsFromProperties(true) }
          }
        }
      }
    }

    return toDo
  }

  override fun execute(updates: List<Update>) {
    @Suppress("DEPRECATION", "UsagesOfObsoleteApi")
    ProgressIndicatorUtils.scheduleWithWriteActionPriority(object : com.intellij.openapi.progress.util.ReadTask() {
      private val events: Set<EventWithType> = eventQueue.getAndSet(ConcurrentCollectionFactory.createConcurrentSet())

      override fun performInReadAction(indicator: ProgressIndicator): Continuation? {
        val toDo = computeInReadAction(events) ?: return null
        return Continuation({
                              if (editor.isValid) {
                                toDo.run()
                              }
                            }, ModalityState.nonModal())
      }

      override fun onCanceled(indicator: ProgressIndicator) {
        eventQueue.updateAndGet {
          it.addAll(events)
          it
        }
        queue(FORCE_UPDATE)
      }
    })
  }
}

private class EventWithType(@JvmField val type: EventType, event: VirtualFileEvent) {
  @JvmField
  val file: VirtualFile = event.file

  private val propertyName: String?
  private val propertyNewValue: Any?

  init {
    if (type == EventType.PROPERTY_CHANGED) {
      propertyName = (event as VirtualFilePropertyEvent).propertyName
      propertyNewValue = event.newValue
    }
    else {
      propertyName = null
      propertyNewValue = null
    }
  }

  fun getPropertyName(): String? {
    LOG.assertTrue(type == EventType.PROPERTY_CHANGED, "Unexpected event type: $type")
    return propertyName
  }

  fun getPropertyNewValue(): Any? {
    LOG.assertTrue(type == EventType.PROPERTY_CHANGED, "Unexpected event type: $type")
    return propertyNewValue
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false

    other as EventWithType

    if (type != other.type) return false
    if (file != other.file) return false
    if (propertyName != other.propertyName) return false
    if (propertyNewValue != other.propertyNewValue) return false

    return true
  }

  override fun hashCode(): Int {
    var result: Int = type.hashCode()
    result = 31 * result + file.hashCode()
    result = 31 * result + (propertyName?.hashCode() ?: 0)
    result = 31 * result + (propertyNewValue?.hashCode() ?: 0)
    return result
  }
}

