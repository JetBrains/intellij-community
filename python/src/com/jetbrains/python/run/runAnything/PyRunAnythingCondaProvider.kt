// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.runAnything

import com.intellij.openapi.actionSystem.DataContext
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.conda.CondaPackageManager
import com.jetbrains.python.packaging.conda.CondaPackageRepository
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.icons.PythonIcons
import javax.swing.Icon

class PyRunAnythingCondaProvider : PyRunAnythingPackageProvider() {
  override fun getHelpCommand() = "conda"

  override fun getHelpGroupTitle(): String = "Python"  // NON-NLS

  override fun getHelpCommandPlaceholder() = "conda <command>"

  override fun getCompletionGroupTitle() = PyBundle.message("python.run.anything.conda.provider")

  override fun getIcon(value: String): Icon {
    return PythonIcons.Python.Anaconda
  }

  override fun getHelpIcon(): Icon {
    return PythonIcons.Python.Anaconda
  }

  override fun getDefaultCommands(): Sequence<String> {
    return CONDA_COMMANDS
  }

  override fun getPackageManager(dataContext: DataContext): PythonPackageManager? {
    val pythonSdk = getSdk(dataContext) ?: return null
    return (PythonPackageManager.forSdk(dataContext.project, pythonSdk) as? CondaPackageManager) ?: return null
  }

  override fun getPackageRepository(dataContext: DataContext): PyPackageRepository? {
    return getPackageManager(dataContext)?.repositoryManager?.repositories?.first { it is CondaPackageRepository }
  }

  override fun getLogCommandType(): CommandType = CommandType.CONDA
}

val CONDA_COMMANDS = listOf("clean", "compare", "config", "create", "help", "info", "init", "install", "list", "package", "remove",
                            "uninstall", "run", "search", "update", "upgrade").asSequence()