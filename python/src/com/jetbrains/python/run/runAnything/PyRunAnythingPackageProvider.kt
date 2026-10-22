// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.runAnything

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.actions.runAnything.RunAnythingAction
import com.intellij.ide.actions.runAnything.activity.RunAnythingCommandLineProvider
import com.intellij.ide.actions.runAnything.activity.RunAnythingCommandProvider
import com.intellij.ide.actions.runAnything.execution.RunAnythingRunProfile
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.python.pyproject.model.evolution.findMainEvoPyProjectIfReady
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.packaging.cache.firstPageOrEmpty
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.intellij.python.requirements.pyRequirement
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.repository.PythonRepositoryManagerBase
import com.intellij.python.sdk.backend.isTargetBased
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Install packages on Python interpreter using Run Anything.
 */
@ApiStatus.Internal
abstract class PyRunAnythingPackageProvider : RunAnythingCommandLineProvider() {
  private var cacheInitialized = AtomicBoolean(false)

  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  override fun suggestCompletionVariants(
    dataContext: DataContext,
    commandLine: CommandLine,
  ): Sequence<String> {
    if (isTargetBased(dataContext)) return emptySequence()
    if (commandLine.parameters.isEmpty() || (commandLine.parameters.size == 1 && !commandLine.toComplete.isEmpty())) {
      return getDefaultCommands()
    }
    val isInstall = commandLine.command.startsWith("install")
    val updateExisting = commandUpdatingExisting(commandLine.command)
    if ((isInstall || updateExisting) &&
        shouldCompletePackageNames(commandLine.parameters, commandLine.toComplete)) {
      val packageManager = getPackageManager(dataContext) ?: return emptySequence()
      initCaches(packageManager)
      if (isInstall) {
        val packageRepository = getPackageRepository(dataContext) ?: return emptySequence()
        val searchResult =
          runBlockingCancellable {
            packageRepository.search(commandLine.toComplete)
          }
        return searchResult.firstPageOrEmpty().getOr { return emptySequence() }.asSequence()
      }
      return packageManager.listInstalledPackagesSnapshot().map { it.name }.filter { it.startsWith(commandLine.toComplete) }.asSequence()
    }
    val last = commandLine.parameters.last()
    if (isInstall) {
      val (ind, operator) = getCompOperatorPosition(last)
      if (ind == null || operator == null || commandLine.toComplete.isBlank()) return emptySequence()
      val packageName = last.substring(0, ind)
      val packageManager = getPackageManager(dataContext) ?: return emptySequence()
      initCaches(packageManager)
      return runBlockingCancellable {
        withContext(Dispatchers.Default) {
          val packageSpec = getPackageRepository(dataContext)?.findPackageSpecification(pyRequirement(packageName)) ?: return@withContext emptySequence()
          val packageInfo = packageManager.repositoryManager.getPackageDetails(packageSpec.name, packageSpec.repository).getOrThrow()
          val versionPrefix = last.substring(ind + operator.length)
          packageInfo.availableVersions.distinct().asSequence().filter { it.startsWith(versionPrefix) }.map { packageName + operator + it }
        }
      }
    }
    return emptySequence()
  }

  protected abstract fun getDefaultCommands(): Sequence<String>

  protected abstract fun getPackageManager(dataContext: DataContext): PythonPackageManager?

  protected abstract fun getPackageRepository(dataContext: DataContext): PyPackageRepository?

  private fun initCaches(packageManager: PythonPackageManager) {
    if (!cacheInitialized.getAndSet(true)) {
      runBlockingCancellable { (packageManager.repositoryManager as? PythonRepositoryManagerBase)?.waitForInit() }
    }
  }

  private fun commandUpdatingExisting(command: String): Boolean {
    return command.startsWith("uninstall") || command.startsWith("remove") || command.startsWith("upgrade")
           || command.startsWith("update")
  }

  private fun getCompOperatorPosition(param: String): Pair<Int?, String?> {
    return compOperator().map { Pair(param.indexOf(it), it) }.filter { it.first != -1 }.firstOrNull() ?: Pair(null, null)
  }

  private fun compOperator(): Sequence<String> = listOf("==", ">=", "<=", ">", "<").asSequence()

  /** The interpreter of the main Python project, or `null` while the model has no snapshot yet. */
  protected fun getInterpreter(dataContext: DataContext): PythonInterpreter? =
    dataContext.project.findMainEvoPyProjectIfReady()?.interpreter

  private fun isTargetBased(dataContext: DataContext): Boolean = getInterpreter(dataContext)?.isTargetBased == true

  /**
   * Complete package name if it's not a command flag or a package version
   */
  private fun shouldCompletePackageNames(parameters: List<String>, toComplete: String): Boolean {
    val allFlags = toComplete.isEmpty() && parameters.drop(1).all { it.startsWith("-") }
    val allPreviousFlags = toComplete.isNotEmpty() && parameters.drop(1).dropLast(1).all { it.startsWith("-") }
    return (allFlags || allPreviousFlags) && getCompOperatorPosition(parameters.last()).first == null
  }

  abstract fun getLogCommandType(): CommandType

  override fun run(dataContext: DataContext, commandLine: CommandLine): Boolean {
    val workDirectory = dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return false
    val executor = dataContext.getData(RunAnythingAction.EXECUTOR_KEY) ?: return false
    if (isTargetBased(dataContext)) {
      return false
    }
    PyRunAnythingCollector.logEvent(getLogCommandType())

    val expectedCommand = helpCommand + " " + commandLine.command
    val packageManager = getPackageManager(dataContext)
    val subscription = if (packageManager != null && commandMutatesInstalledPackages(commandLine.command)) {
      subscribeForPackageReload(dataContext.project, packageManager, expectedCommand)
    }
    else {
      null
    }

    RunAnythingCommandProvider.runCommand(workDirectory, expectedCommand, executor, dataContext)

    // `runCommand` swallows ExecutionException from `buildAndExecute`: on failure processStartScheduled
    // never fires and the listener would leak until the project closes. processStartScheduled is published
    // synchronously inside `buildAndExecute`, so by the time control reaches this line the listener has
    // either bound to this run or will never bind — drop it in the latter case.
    if (subscription != null && !subscription.isBound) {
      Disposer.dispose(subscription.disposable)
    }
    return true
  }

  /**
   * `pip install` / `uninstall` / `install --upgrade` mutate the interpreter's package set. For
   * read-only commands (`list`, `show`, `freeze`, …) a reload is wasted work and would also fire on
   * `--help` or a syntactically invalid command. The subset here mirrors [commandUpdatingExisting]
   * plus the install family.
   */
  private fun commandMutatesInstalledPackages(command: String): Boolean =
    command.startsWith("install") || commandUpdatingExisting(command)

  /**
   * The shell command runs as an external process, so [PythonPackageManager] does not observe the mutation.
   * Hook onto [ExecutionManager.EXECUTION_TOPIC] and trigger a reload once this specific execution terminates,
   * so inspections depending on the installed-package snapshot update without the user having to defocus the file.
   *
   * Matching uses the `executionId` captured in [ExecutionListener.processStartScheduled]: the command text
   * alone would collapse concurrent / repeated runs of the same command into one shared listener.
   *
   * The returned [PackageReloadSubscription] exposes whether the listener bound to a run and the [Disposable]
   * the caller can release if `runCommand` fails to publish a start event; otherwise [ExecutionListener.processTerminated]
   * (or [ExecutionListener.processNotStarted]) disposes it after the run resolves.
   */
  private fun subscribeForPackageReload(project: Project, packageManager: PythonPackageManager, expectedCommand: String): PackageReloadSubscription {
    // Parent to a project-scoped service so the listener is released on project close even if neither
    // processNotStarted nor processTerminated ever fires for our execution id.
    val listenerDisposable = Disposer.newDisposable("PyRunAnythingPackageProvider reload: $expectedCommand")
    Disposer.register(PyPackageCoroutine.getInstance(project), listenerDisposable)
    val connection = project.messageBus.connect(listenerDisposable)
    val boundExecutionId = AtomicLong(UNBOUND_EXECUTION_ID)
    connection.subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
      override fun processStartScheduled(executorId: String, env: ExecutionEnvironment) {
        if (boundExecutionId.get() != UNBOUND_EXECUTION_ID) return
        if (!env.matchesCommand(expectedCommand)) return
        boundExecutionId.compareAndSet(UNBOUND_EXECUTION_ID, env.executionId)
      }

      override fun processNotStarted(executorId: String, env: ExecutionEnvironment, cause: Throwable?) {
        if (!env.matchesExecution(boundExecutionId.get())) return
        Disposer.dispose(listenerDisposable)
      }

      override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
        if (!env.matchesExecution(boundExecutionId.get())) return
        Disposer.dispose(listenerDisposable)
        if (exitCode != 0) return
        PyPackageCoroutine.launch(project) {
          packageManager.reloadPackages()
        }
      }
    })
    return PackageReloadSubscription(listenerDisposable, boundExecutionId)
  }

  private class PackageReloadSubscription(val disposable: Disposable, private val boundExecutionId: AtomicLong) {
    val isBound: Boolean
      get() = boundExecutionId.get() != UNBOUND_EXECUTION_ID
  }

  private fun ExecutionEnvironment.matchesCommand(expectedCommand: String): Boolean {
    val profile = runProfile
    return profile is RunAnythingRunProfile && profile.originalCommand == expectedCommand
  }

  private fun ExecutionEnvironment.matchesExecution(boundExecutionId: Long): Boolean =
    boundExecutionId != UNBOUND_EXECUTION_ID && executionId == boundExecutionId

  companion object {
    private const val UNBOUND_EXECUTION_ID: Long = -1L
  }
}