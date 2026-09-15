// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.standaloneAnalysis.platform.smoke

import com.intellij.core.CoreApplicationEnvironment
import com.intellij.core.CoreProjectEnvironment
import com.intellij.diagnostic.PluginException
import com.intellij.lang.LanguageASTFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.fileTypes.PlainTextParserDefinition
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiAnchor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.impl.source.tree.PlainTextASTFactory
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.concurrency.AppScheduledExecutorService
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

@ApiStatus.Internal
object PlatformStandaloneSmoke {
  @JvmStatic
  fun main(args: Array<String>) {
    try {
      run()
    }
    catch (e: Throwable) {
      // A failed check must end the process also when a leaked thread would keep it alive.
      e.printStackTrace()
      exitProcess(1)
    }
  }

  private fun run() {
    val applicationDisposable = Disposer.newDisposable("PlatformStandaloneSmoke application")
    val projectDisposable = Disposer.newDisposable("PlatformStandaloneSmoke project")

    val project = try {
      val environment = CoreApplicationEnvironment(applicationDisposable, false, true)
      check(ApplicationManager.getApplication() === environment.application) { "The environment application is not the application" }
      pooledThreadIsDaemon()

      environment.registerFileType(PlainTextFileType.INSTANCE, "info")
      environment.registerParserDefinition(PlainTextParserDefinition())
      environment.addExplicitExtension(LanguageASTFactory.INSTANCE, PlainTextLanguage.INSTANCE, PlainTextASTFactory())

      val projectEnvironment = CoreProjectEnvironment(projectDisposable, environment)

      val checks = Checks(projectEnvironment.project, projectDisposable)
      checks.plainTextFileParses()
      checks.psiChangeUpdatesTheTreeAndNotifiesTheListeners()
      checks.smartPointerAndAnchorRestoreElement()
      checks.fileScopeContainsFile()
      checks.pluginProblemIsReportedAsPluginException()

      projectEnvironment.project
    }
    finally {
      Disposer.dispose(projectDisposable)
      Disposer.dispose(applicationDisposable)
    }
    check(project.isDisposed) { "The project is not disposed with the environment" }
    check(ApplicationManager.getApplication() == null) { "The application is not released after the disposal" }

    // Application can be disposed and recreated later
    val secondDisposable = Disposer.newDisposable("PlatformStandaloneSmoke second application")
    try {
      val environment = CoreApplicationEnvironment(secondDisposable, false, true)
      check(ApplicationManager.getApplication() === environment.application) { "The second application is not the application" }
      pooledThreadIsDaemon()
    }
    finally {
      Disposer.dispose(secondDisposable)
    }
    check(ApplicationManager.getApplication() == null) { "The second application is not released after the disposal" }
  }

  /**
   * A task on the application pool starts the pool. An environment in headless mode configures the pool with daemon threads, so the
   * pool does not keep the process alive after the disposal, see KT-73127.
   */
  private fun pooledThreadIsDaemon() {
    val thread = ApplicationManager.getApplication().executeOnPooledThread(Callable { Thread.currentThread() }).get(10, TimeUnit.SECONDS)
    check(thread.name.startsWith(AppScheduledExecutorService.POOLED_THREAD_PREFIX)) { "The task runs on unexpected pool: ${thread.name}" }
    check(thread.isDaemon) { "A pooled thread is not daemon: ${thread.name}" }
  }

  private class Checks(private val project: Project, private val disposable: Disposable) {
    fun plainTextFileParses() {
      val file = parse("standalone")
      check(file.language == PlainTextLanguage.INSTANCE) { "Unexpected language: ${file.language}" }
      check(file.text == "standalone") { "Unexpected text: ${file.text}" }
      check(file.firstChild != null) { "The file has no tree" }
    }

    /**
     * A PSI change runs a POM transaction inside an undo-transparent action of the `CommandProcessor`. The transaction copies the new element through the
     * `TreeCopyHandler` and `TreeGenerator` points, reads the indent through the `IndentHelper`, fires the tree change events through
     * the `PsiTreeChangePreprocessor` and `PsiTreeChangeListener` points, increments the modification tracker, whose event asserts the
     * `TransactionGuard` state, and writes the change to the document.
     */
    fun psiChangeUpdatesTheTreeAndNotifiesTheListeners() {
      val file = parse("standalone")

      val events = ArrayList<String>()
      PsiManager.getInstance(project).addPsiTreeChangeListenerBackgroundable(EventCollector(events), disposable)

      val plainEvents = ArrayList<String>()
      PsiManager.getInstance(project).addPsiTreeChangeListener(EventCollector(plainEvents), disposable)
      val tracker = PsiModificationTracker.getInstance(project)
      val countBefore = tracker.modificationCount

      val replacement = parse("standalone works").firstChild
      CommandProcessor.getInstance().runUndoTransparentAction {
        ApplicationManager.getApplication().runWriteAction { file.firstChild.replace(replacement) }
      }

      check(file.text == "standalone works") { "The PSI change is not applied: ${file.text}" }
      check(tracker.modificationCount > countBefore) { "The modification tracker did not notice the change" }
      check(events.isNotEmpty()) { "No tree change event is fired" }
      check(plainEvents.isNotEmpty()) { "No tree change event reaches the listener added by hand" }
      val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: error("The file has no document")
      check(document.text == "standalone works") { "The document is not synchronized with the PSI: ${document.text}" }
    }

    fun smartPointerAndAnchorRestoreElement() {
      val file = parse("standalone")
      val leaf = file.firstChild

      val pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(leaf)
      check(pointer.element === leaf) { "The smart pointer does not restore the element: ${pointer.element}" }

      val anchor = PsiAnchor.create(leaf)
      check(anchor.retrieve() === leaf) { "The anchor does not restore the element: ${anchor.retrieve()}" }
    }

    fun fileScopeContainsFile() {
      val inScope = parse("in").virtualFile
      val outOfScope = parse("out").virtualFile
      val scope = GlobalSearchScope.filesScope(project, listOf(inScope))

      // A files scope keeps its files in a compact virtual file set from the `VirtualFileSetFactory` service.
      check(scope.contains(inScope)) { "The files scope does not contain its file" }
      check(!scope.contains(outOfScope)) { "The files scope contains a foreign file" }
    }

    fun pluginProblemIsReportedAsPluginException() {
      val exception = PluginException.createByClass("The smoke problem", null, PlatformStandaloneSmoke::class.java)

      // The platform reports a problem in a class through the `PluginProblemReporter` service.
      // Without the service the report is `null`.
      check(exception.message.startsWith("The smoke problem")) { "Unexpected problem message: ${exception.message}" }
    }

    private fun parse(text: String): PsiFile {
      val fileFactory = PsiFileFactory.getInstance(project)
      return fileFactory.createFileFromText(
        /* name = */ "note.info",
        /* language = */ PlainTextLanguage.INSTANCE,
        /* text = */ text,
        /* supportsSendingPsiEvents = */ true,
        /* markAsCopy = */ false
      )
    }
  }

  private class EventCollector(private val events: MutableList<String>) : PsiTreeChangeAdapter() {
    override fun childReplaced(event: PsiTreeChangeEvent) {
      events.add("replaced")
    }

    override fun childrenChanged(event: PsiTreeChangeEvent) {
      events.add("changed")
    }
  }
}
