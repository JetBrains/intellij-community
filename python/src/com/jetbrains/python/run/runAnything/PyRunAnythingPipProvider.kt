// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.runAnything

import com.intellij.ide.actions.runAnything.RunAnythingAction
import com.intellij.ide.actions.runAnything.activity.RunAnythingCommandLineProvider
import com.intellij.ide.actions.runAnything.activity.RunAnythingCommandProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.pip.PipPythonPackageManager
import com.jetbrains.python.sdk.isTargetBased
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.statistics.modules
import icons.PythonIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.Icon

class PyRunAnythingPipProvider : RunAnythingCommandLineProvider() {
  override fun getHelpCommand() = "pip"

  override fun getHelpGroupTitle(): String = "Python"  // NON-NLS

  override fun getHelpCommandPlaceholder() = "pip <command>"

  override fun getCompletionGroupTitle() = PyBundle.message("python.run.anything.pip.provider")

  override fun getIcon(value: String): Icon {
    return PythonIcons.Python.Python
  }

  override fun getHelpIcon(): Icon {
    return PythonIcons.Python.Python
  }

  override fun suggestCompletionVariants(dataContext: DataContext,
                                         commandLine: CommandLine): Sequence<String> {
    if (getSdk(dataContext)?.isTargetBased() == true) return emptySequence()
    if (commandLine.parameters.isEmpty() || (commandLine.parameters.size == 1 && !commandLine.toComplete.isEmpty())) {
      return PIP_COMMANDS
    }
    val isInstall = commandLine.command.startsWith("install")
    val isUninstall = commandLine.command.startsWith("uninstall")
    if ((isInstall || isUninstall) &&
        shouldCompletePackageNames(commandLine.parameters, commandLine.toComplete)) {
      val packageManager = getPackageManager(dataContext) ?: return emptySequence()
      if (isInstall) {
        return packageManager.repositoryManager.allPackages().filter {
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
      val packageSpec = packageManager.repositoryManager.repositories.first().createPackageSpecification(packageName)
      return runBlockingCancellable {
        withContext(Dispatchers.Default) {
          val packageInfo = packageManager.repositoryManager.getPackageDetails(packageSpec)
          val versionPrefix = last.substring(ind + operator.length)
          packageInfo.availableVersions.asSequence().filter { it.startsWith(versionPrefix) }.map { packageName + operator + it }
        }
      }
    }
    return emptySequence()
  }

  private fun getCompOperatorPosition(param: String): Pair<Int?, String?> {
    return compOperator().map { Pair(param.indexOf(it), it) }.filter { it.first != -1 }.firstOrNull() ?: Pair(null, null)
  }

  private fun compOperator(): Sequence<String> {
    return listOf("==", ">=", "<=", ">", "<").asSequence()
  }

  private fun getSdk(dataContext: DataContext): Sdk? {
    return dataContext.project.modules.firstOrNull()?.pythonSdk
  }

  private fun getPackageManager(dataContext: DataContext): PipPythonPackageManager? {
    val pythonSdk = getSdk(dataContext) ?: return null
    return (PythonPackageManager.forSdk(dataContext.project, pythonSdk) as? PipPythonPackageManager) ?: return null
  }

  private fun shouldCompletePackageNames(parameters: List<String>, toComplete: String): Boolean {
    val allFlags = toComplete.isEmpty() && parameters.drop(1).all { it.startsWith("-") }
    val allPreviousFlags = toComplete.isNotEmpty() && parameters.drop(1).dropLast(1).all { it.startsWith("-") }
    return (allFlags || allPreviousFlags) && getCompOperatorPosition(parameters.last()).first == null
  }

  override fun run(dataContext: DataContext, commandLine: CommandLine): Boolean {
    val workDirectory = dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return false
    val executor = dataContext.getData(RunAnythingAction.EXECUTOR_KEY) ?: return false
    if (getSdk(dataContext)?.isTargetBased() == true) {
      return false
    }
    PyRunAnythingCollector.Util.logEvent(CommandType.PIP)

    RunAnythingCommandProvider.runCommand(workDirectory, helpCommand + " " + commandLine.command, executor, dataContext)
    return true
  }
}

val PIP_COMMANDS = listOf("install", "download", "uninstall", "freeze", "list", "show", "wheel", "hash", "check", "config", "cache",
                          "completion", "help").asSequence()