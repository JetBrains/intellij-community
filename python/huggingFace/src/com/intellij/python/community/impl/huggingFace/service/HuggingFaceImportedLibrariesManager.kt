// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.python.community.impl.huggingFace.HuggingFaceUtil
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class HuggingFaceImportedLibrariesManager(val project: Project) {
  private var cachedResult: Result? = null
  private var cacheTimestamp: Long = 0
  private val invalidationThresholdMinutes = 5
  private var changesCount: Int = 0
  private val changesThreshold = 10

  private data class Result(val isImported: Boolean)

  init {
    val connection: MessageBusConnection = project.messageBus.connect()
    connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        if (events.any { it.file?.extension in listOf("py", "ipynb") }) {
          changesCount++
          if (changesCount >= changesThreshold) {
            invalidate()
          }
        }
      }
    })

    EditorFactory.getInstance().eventMulticaster.addDocumentListener(object: DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        if (event.newFragment.toString().contains("import")) {
          invalidate()
        }
      }
    }, connection)
  }

  fun isLibraryImported(): Boolean {
    val project = this.project
    if (System.currentTimeMillis() - cacheTimestamp > invalidationThresholdMinutes * 60 * 1000 || cachedResult == null) {
      val isImported = HuggingFaceUtil.isAnyHFLibraryImportedInProject(project)
      if (isImported) {
        cachedResult = Result(true)
        HuggingFaceUtil.triggerCacheFillIfNeeded(project)
      }
      cacheTimestamp = System.currentTimeMillis()
    }
    return cachedResult?.isImported ?: false
  }

  private fun invalidate() {
    if (cachedResult?.isImported != true) {
      cachedResult = null
    }
    cacheTimestamp = 0
    changesCount = 0
  }
}
