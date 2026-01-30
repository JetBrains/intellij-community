// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions

import com.intellij.application.options.editor.WebEditorOptions
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.lang.Language
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.xhtml.XHTMLLanguage
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.TestOnlyThreading
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.CommandListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.XmlTypedHandlersAdditionalSupport
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.templateLanguages.TemplateLanguage
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.annotations.TestOnly
import java.lang.Runnable
import java.lang.System
import java.lang.Thread
import java.util.concurrent.TimeoutException
import kotlin.streams.asSequence

@Service(Service.Level.PROJECT)
class XmlTagNameSynchronizer(private val project: Project, val cs: CoroutineScope) {

  companion object {

    @JvmStatic
    fun runWithoutCancellingSyncTagsEditing(document: Document, runnable: Runnable) {
      document.putUserData(SKIP_COMMAND, true)
      try {
        runnable.run()
      }
      finally {
        document.putUserData(SKIP_COMMAND, null)
      }
    }

    @TestOnly
    @JvmStatic
    fun getInstance(project: Project): XmlTagNameSynchronizer = project.service()

    internal val SKIP_COMMAND: Key<Boolean> = Key.create("tag.name.synchronizer.skip.command")

    internal val SYNCHRONIZER_KEY: Key<XmlTagNameSynchronizerImpl> = Key.create("tag_name_synchronizer")

    private val SUPPORTED_LANGUAGES = setOf(HTMLLanguage.INSTANCE, XMLLanguage.INSTANCE, XHTMLLanguage)

  }

  @TestOnly
  @RequiresEdt
  fun waitForSynchronizersCreation() {
    if (writeLock.availablePermits > 0) return
    // Cannot use dispatchAllInvocationEvents() if write access is allowed.
    val application = ApplicationManager.getApplication()
    if (application.isWriteAccessAllowed)
      return
    val start = System.currentTimeMillis()
    val job = cs.coroutineContext.job
    while (job.children.toList().isNotEmpty() && cs.coroutineContext.isActive) {
      TestOnlyThreading.releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack {  }
      // do not release the WI lock here! Causes accidental dumb mode appearance
      UIUtil.dispatchAllInvocationEvents()
      Thread.sleep(1)
      // The read action in `ensureSynchronizerCreated` will not be executed
      // if write intent lock is acquired, so no point waiting.
      if (application.isWriteIntentLockAcquired) {
        return
      }
      if (System.currentTimeMillis() - start > 2000) {
        thisLogger().warn("Timed out waiting for synchronizers to be created.", TimeoutException())
        return
      }
    }
  }

  private val writeLock = Semaphore(1)

  init {
    @Suppress("TestOnlyProblems")
    if (ApplicationManager.getApplication().isUnitTestMode) {
      val messageBus = project.messageBus.connect()
      messageBus.subscribe(AnActionListener.TOPIC, object : AnActionListener {
        override fun beforeShortcutTriggered(shortcut: Shortcut, actions: List<AnAction?>, dataContext: DataContext) {
          waitForSynchronizersCreation()
        }

        override fun beforeEditorTyping(c: Char, dataContext: DataContext) {
          waitForSynchronizersCreation()
        }
      })
      messageBus.subscribe(CommandListener.TOPIC, object : CommandListener {
        override fun commandStarted(event: CommandEvent) {
          waitForSynchronizersCreation()
        }

        override fun undoTransparentActionStarted() {
          waitForSynchronizersCreation()
        }
      })
    }
  }

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

  private fun scheduleEnsureSynchronizerCreated(editor: EditorImpl) {
    cs.launch {
      writeLock.withPermit {
        ensureSynchronizerCreated(editor)
      }
    }
  }

  private suspend fun ensureSynchronizerCreated(editor: EditorImpl) {
    if (editor.getUserData(SYNCHRONIZER_KEY) != null) return
    readAction {
      if (editor.isDisposed || project.isDisposed()) return@readAction null
      val document = editor.document
      val file = FileDocumentManager.getInstance().getFile(document)
      findXmlLikeLanguage(project, file)
    }?.let { language ->
      withContext(Dispatchers.EDT) {
        if (editor.isDisposed || project.isDisposed()) return@withContext
        XmlTagNameSynchronizerImpl(editor, project, language).listenForDocumentChanges()
      }
    }
  }

  private fun findXmlLikeLanguage(project: Project, file: VirtualFile?): Language? {
    val psiFile = file?.takeIf { it.isValid }?.let { PsiManager.getInstance(project).findFile(it) }
                  ?: return null
    for (language in psiFile.getViewProvider().getLanguages()) {
      if (SUPPORTED_LANGUAGES.find { language.isKindOf(it) } != null
          && language !is TemplateLanguage && language !is ExternallyTagSynchronizedLanguage
          || XmlTypedHandlersAdditionalSupport.supportsTypedHandlers(psiFile, language)
      ) {
        return language
      }
    }
    return null
  }

  class MyEditorFactoryListener : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
      event.editor.scheduleEnsureSynchronizerCreated()
    }

    private fun Editor.scheduleEnsureSynchronizerCreated() {
      project?.service<XmlTagNameSynchronizer>()?.scheduleEnsureSynchronizerCreated(this as? EditorImpl ?: return)
    }
  }

  class MyCommandListener : CommandListener {
    override fun beforeCommandFinished(event: CommandEvent) {
      findSynchronizers(event.document).forEach { it.beforeCommandFinished() }
    }

    private fun findSynchronizers(document: Document?): Sequence<XmlTagNameSynchronizerImpl> =
      if (document == null || !WebEditorOptions.getInstance().isSyncTagEditing)
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
        project?.service<XmlTagNameSynchronizer>()?.recreateSynchronizersFor(editors.filterIsInstance<EditorImpl>())
      }
    }
  }
}