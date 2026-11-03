// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.runAnything

import com.intellij.execution.RunContentExecutor
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.ide.actions.runAnything.activity.RunAnythingCommandLineProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.python.community.execService.ProcessEvent
import com.intellij.python.pixi.backend.PixiPyTool
import com.intellij.python.pixi.backend.PyPixiBackendBundle
import com.intellij.python.pixi.backend.packaging.PixiPackageManager
import com.intellij.python.pixi.common.icons.PythonPixiCommonIcons
import com.intellij.python.pyproject.model.evolution.findMainEvoPyProjectIfReady
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.getOrNull
import com.jetbrains.python.packaging.cache.PythonPackageSearchResult
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.withContext
import java.io.OutputStream
import javax.swing.Icon

internal class PixiRunAnythingProvider : RunAnythingCommandLineProvider() {
  override fun getHelpCommand(): String = "pixi"
  override fun getHelpCommandPlaceholder(): String = "pixi <command>"
  override fun getHelpGroupTitle(): String = PyPixiBackendBundle.message("python.pixi.run.anything.group")
  override fun getCompletionGroupTitle(): String = PyPixiBackendBundle.message("python.pixi.run.anything.title")
  override fun getHelpIcon(): Icon = PythonPixiCommonIcons.Pixi
  override fun getIcon(value: String): Icon = PythonPixiCommonIcons.Pixi

  @RequiresBackgroundThread
  override fun suggestCompletionVariants(dataContext: DataContext, commandLine: CommandLine): Sequence<String> {
    val command = commandLine.completedParameters.firstOrNull()
                  ?: return COMMANDS.asSequence().filter { it.startsWith(commandLine.toComplete) }
    if (commandLine.toComplete.startsWith('-') ||
        commandLine.completedParameters.drop(1).any { it.startsWith('-') && it !in COMPLETION_FLAGS }) {
      return emptySequence()
    }
    if (command !in PACKAGE_COMMANDS) return emptySequence()
    val manager = manager(dataContext) ?: return emptySequence()
    val packages = runBlockingCancellable {
      manager.waitForInit()
      if (command != "add") {
        return@runBlockingCancellable manager.commandPackages().getOr { return@runBlockingCancellable emptyList() }
          .filter { command != "remove" || (it.kind == "pypi") == ("--pypi" in commandLine.completedParameters) }
          .map { it.name }
          .filter { it.startsWith(commandLine.toComplete) }
      }
      val repositories = manager.repositoryManager
      val repository = if ("--pypi" in commandLine.completedParameters) {
        repositories.waitForInit()
        repositories.repositories.firstOrNull { it != repositories.nativeRepository } ?: return@runBlockingCancellable emptyList()
      }
      else repositories.nativeRepository
      val version = VERSION_REQUIREMENT.matchEntire(commandLine.toComplete)
      if (version != null) {
        val [name, operator, prefix] = version.destructured
        return@runBlockingCancellable repositories.getVersions(name, repository).orEmpty()
          .filter { it.startsWith(prefix) }.map { name + operator + it }
      }
      pixiCommandCompletionNames(repository.search(commandLine.toComplete), commandLine.toComplete)
    }
    return packages.asSequence()
  }

  override fun run(dataContext: DataContext, commandLine: CommandLine): Boolean {
    val manager = manager(dataContext) ?: return false
    val project = manager.project
    val title = PyPixiBackendBundle.message("python.pixi.run.anything.title")
    lateinit var commandJob: Job
    val handler = PixiCommandProcessHandler { commandJob.cancel() }
    var exitCode = 1
    commandJob = PyPackageCoroutine.launch(project, Dispatchers.IO, CoroutineStart.LAZY) {
      withContext(Dispatchers.EDT) {
        RunContentExecutor(project, handler).withTitle(title).withStop({ commandJob.cancel() }, { commandJob.isActive }).run()
      }
      val result = PythonPackageManagerUI.forPackageManager(manager).executeCommand(title) {
        manager.executeCommandLine(commandLine.parameters, FlowCollector { event ->
          when (event) {
            is ProcessEvent.ProcessStarted, is ProcessEvent.ProcessEnded -> Unit
            is ProcessEvent.ProcessOutput -> handler.notifyTextAvailable(event.line, when (event.stream) {
              ProcessEvent.OutputType.STDOUT -> ProcessOutputTypes.STDOUT
              ProcessEvent.OutputType.STDERR -> ProcessOutputTypes.STDERR
            })
          }
        })
      }
      if (result != null) exitCode = 0
    }
    commandJob.invokeOnCompletion { handler.finished(exitCode) }
    commandJob.start()
    return true
  }

  private fun manager(dataContext: DataContext): PixiPackageManager? {
    val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return null
    val interpreter = project.findMainEvoPyProjectIfReady()?.interpreter ?: return null
    if (interpreter.ref.manager != PixiPyTool.getInstance().fusId) return null
    return PythonPackageManager.forPythonInterpreter(project, interpreter) as? PixiPackageManager
  }
}

private class PixiCommandProcessHandler(private val cancel: () -> Unit) : ProcessHandler() {
  override fun destroyProcessImpl() = cancel()
  override fun detachProcessImpl() {
    notifyProcessDetached()
    cancel()
  }

  override fun detachIsDefault(): Boolean = false
  override fun getProcessInput(): OutputStream? = null

  fun finished(exitCode: Int) {
    if (!isProcessTerminated) notifyProcessTerminated(exitCode)
  }
}

private val COMMANDS = listOf("add", "remove", "update", "upgrade", "install", "list", "run", "info", "search", "workspace", "task")
private val PACKAGE_COMMANDS = setOf("add", "remove", "update", "upgrade")
private val COMPLETION_FLAGS = setOf("--pypi", "--no-install", "--no-progress", "--frozen", "--locked")
private val VERSION_REQUIREMENT = Regex("^([^<>=!~]+)(==|>=|<=|!=|~=|=|>|<)(.*)$")

internal fun pixiCommandCompletionNames(result: PythonPackageSearchResult, prefix: String): List<String> =
  result.pages.asSequence().flatMap { it.contents().getOrNull().orEmpty().asSequence() }
    .filter { it.startsWith(prefix) }.take(MAX_PACKAGE_COMPLETIONS).toList()

private const val MAX_PACKAGE_COMPLETIONS = 100
