// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.editor

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.CommandListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.project.MavenProjectsManager
import kotlin.streams.asSequence

@Service(Service.Level.PROJECT)
class MavenModelVersionSynchronizerService(private val project: Project, val cs: CoroutineScope) {

  companion object {
    @JvmField
    internal val SYNCHRONIZER_KEY: Key<MavenModelVersionSyncronizerImpl> = Key.create("maven.version.xml.sync")

    @JvmField
    internal val SKIP_COMMAND_KEY: Key<Boolean> = Key.create("maven.version.synchronizer.skip.command")
  }

  private val writeLock = Semaphore(1)


  private fun recreateSynchronizersFor(editors: List<EditorImpl>) {
    cs.launch {
      writeLock.withPermit {
        editors.forEach { editor ->
          editor.getUserData(SYNCHRONIZER_KEY)?.let {
            Disposer.dispose(it)
          }
          ensureSynchronizerCreated(editor)
        }
      }
    }
  }

  fun scheduleEnsureSynchronizerCreated(editor: EditorImpl) {
    cs.launch {
      writeLock.withPermit {
        ensureSynchronizerCreated(editor)
      }
    }
  }

  private suspend fun ensureSynchronizerCreated(editor: EditorImpl) {
    if (!Registry.`is`("maven.sync.model.editing")) return
    if (editor.getUserData(SYNCHRONIZER_KEY) != null) return

    val mavenProjectManager = MavenProjectsManager.getInstanceIfCreated(project) ?: return
    val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
    if (mavenProjectManager.findProject(file) == null) return
    withContext(Dispatchers.EDT) {
      if (editor.isDisposed || project.isDisposed()) return@withContext
      MavenModelVersionSyncronizerImpl(editor, project).listenForDocumentChanges()
    }
  }


  class MyCommandListener : CommandListener {
    override fun beforeCommandFinished(event: CommandEvent) {
      findSynchronizers(event.document).forEach { it.beforeCommandFinished() }
    }

    private fun findSynchronizers(document: Document?): Sequence<MavenModelVersionSyncronizerImpl> =
      if (document == null || !Registry.`is`("maven.sync.model.editing"))
        emptySequence()
      else
        EditorFactory.getInstance().editors(document, null)
          .asSequence()
          .mapNotNull { editor -> editor.getUserData(SYNCHRONIZER_KEY) }
  }

  class MyDynamicPluginListener : DynamicPluginListener {
    override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
      recreateSynchronizers()
    }

    override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
      recreateSynchronizers()
    }

    private fun recreateSynchronizers() {
      EditorFactory.getInstance().getAllEditors().groupBy { it.project }.forEach { (project, editors) ->
        project?.service<MavenModelVersionSynchronizerService>()?.recreateSynchronizersFor(editors.filterIsInstance<EditorImpl>())
      }
    }
  }
}

class MavenModelVersionEditorFactoryListener : EditorFactoryListener {
  override fun editorCreated(event: EditorFactoryEvent) {
    val editor = event.editor
    val project = editor.project
    project?.service<MavenModelVersionSynchronizerService>()?.scheduleEnsureSynchronizerCreated(editor as? EditorImpl ?: return)
  }
}

