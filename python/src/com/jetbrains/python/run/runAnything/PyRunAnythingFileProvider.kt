// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.runAnything

import com.intellij.execution.RunManager
import com.intellij.execution.actions.ChooseRunConfigurationPopup
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.execution.impl.statistics.RunConfigurationOptionUsagesCollector.logAddNew
import com.intellij.ide.actions.runAnything.RunAnythingAction
import com.intellij.ide.actions.runAnything.RunAnythingUtil
import com.intellij.ide.actions.runAnything.activity.RunAnythingCommandLineProvider
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.python.PyBundle
import com.jetbrains.python.run.PythonConfigurationType
import javax.swing.Icon

class PyRunAnythingFileProvider : RunAnythingCommandLineProvider() {
  override fun getHelpCommand() = "python"

  override fun getHelpGroupTitle(): String = "Python"  // NON-NLS

  override fun getHelpCommandPlaceholder() = "python <file name>"

  override fun getCompletionGroupTitle() = PyBundle.message("python.run.anything.file.provider")

  override fun getIcon(value: String): Icon? {
    return PythonConfigurationType.getInstance().factory.icon
  }

  override fun getHelpIcon(): Icon? {
    return PythonConfigurationType.getInstance().factory.icon
  }

  override fun suggestCompletionVariants(dataContext: DataContext, commandLine: CommandLine): Sequence<String> {
    if (commandLine.toComplete.startsWith("-") && commandLine.parameters.all { it.startsWith("-") }) {
      return PYTHON_FLAGS
    }
    if (!shouldCompleteFilename(commandLine.parameters, commandLine.toComplete) || !isProjectOrModuleContext(dataContext)) {
      return emptySequence()
    }
    val project = dataContext.project
    val pyNames = ReadAction.compute<List<@NlsSafe String>, Throwable> {
      FilenameIndex.getAllFilesByExt(project, "py", GlobalSearchScope.projectScope(project))
        .mapNotNull { if (it.name.startsWith(commandLine.toComplete)) it.name else null }
    }
    return pyNames.asSequence()
  }

  private fun shouldCompleteFilename(parameters: List<String>, toComplete: String): Boolean {
    val allFlags = toComplete.isEmpty() && parameters.all { it.startsWith("-") }
    val allPreviousFlags = toComplete.isNotEmpty() && parameters.dropLast(1).all { it.startsWith("-") }
    return (allFlags || allPreviousFlags) && !parameters.toSet().contains("-m")
  }

  override fun run(dataContext: DataContext, commandLine: CommandLine): Boolean {
    val configuration = createPythonConfiguration(dataContext, "$helpCommand ${commandLine.command}",
                                                  PythonConfigurationType.getInstance().factory)
    try {
      configuration.checkSettings()
    }
    catch (e: RuntimeConfigurationException) {
      return false
    }
    val executor = RunAnythingAction.EXECUTOR_KEY.getData(dataContext) ?: return false
    val project = RunAnythingUtil.fetchProject(dataContext)
    if (!RunManager.getInstance(project).hasSettings(configuration)) {
      RunManager.getInstance(project).addConfiguration(configuration)
      logAddNew(project, configuration.type.id, ActionPlaces.RUN_ANYTHING_POPUP)
    }
    PyRunAnythingCollector.logEvent(CommandType.PYTHON)

    ChooseRunConfigurationPopup.ItemWrapper.wrap(dataContext.project, configuration).perform(project, executor, dataContext)
    return true
  }
}

val PYTHON_FLAGS = listOf("-b", "-B", "-c", "-d", "-E", "-h", "-i", "-I", "-m", "-O", "-OO", "-q", "-s", "-S", "-u", "-v", "-V", "-W",
                          "-x", "-X").asSequence()