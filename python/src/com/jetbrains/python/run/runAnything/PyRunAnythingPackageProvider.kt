// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.runAnything

import com.intellij.ide.actions.runAnything.RunAnythingAction
import com.intellij.ide.actions.runAnything.activity.RunAnythingCommandLineProvider
import com.intellij.ide.actions.runAnything.activity.RunAnythingCommandProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.sdk.isTargetBased
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.statistics.modules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Install packages on Python interpreter using Run Anything.
 */
abstract class PyRunAnythingPackageProvider : RunAnythingCommandLineProvider() {
  private var cacheInitialized = AtomicBoolean(false)

  @RequiresBackgroundThread
  override fun suggestCompletionVariants(dataContext: DataContext,
                                         commandLine: CommandLine): Sequence<String> {
    if (getSdk(dataContext)?.isTargetBased() == true) return emptySequence()
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
        return packageRepository.getPackages().filter {
          it.startsWith(commandLine.toComplete)
        }.asSequence()
      }
      return packageManager.installedPackages.map { it.name }.filter { it.startsWith(commandLine.toComplete) }.asSequence()
    }
    val last = commandLine.parameters.last()
    if (isInstall) {
      val (ind, operator) = getCompOperatorPosition(last)
      if (ind == null || operator == null || commandLine.toComplete.isBlank()) return emptySequence()
      val packageName = last.substring(0, ind)
      val packageManager = getPackageManager(dataContext) ?: return emptySequence()
      initCaches(packageManager)
      val packageSpec = getPackageRepository(dataContext)?.createPackageSpecification(packageName) ?: return emptySequence()
      return runBlockingCancellable {
        withContext(Dispatchers.Default) {
          val packageInfo = packageManager.repositoryManager.getPackageDetails(packageSpec)
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
      runBlockingCancellable { packageManager.repositoryManager.initCaches() }
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

  protected fun getSdk(dataContext: DataContext): Sdk? = dataContext.project.modules.firstOrNull()?.pythonSdk

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
    if (getSdk(dataContext)?.isTargetBased() == true) {
      return false
    }
    PyRunAnythingCollector.logEvent(getLogCommandType())

    RunAnythingCommandProvider.runCommand(workDirectory, helpCommand + " " + commandLine.command, executor, dataContext)
    return true
  }
}