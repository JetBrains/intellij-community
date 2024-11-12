// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.runAnything

import com.intellij.openapi.actionSystem.DataContext
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.repository.PyPIPackageRepository
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.psi.icons.PythonPsiApiIcons
import javax.swing.Icon

class PyRunAnythingPipProvider : PyRunAnythingPackageProvider() {
  override fun getHelpCommand() = "pip"

  override fun getHelpGroupTitle(): String = "Python"  // NON-NLS

  override fun getHelpCommandPlaceholder() = "pip <command>"

  override fun getCompletionGroupTitle() = PyBundle.message("python.run.anything.pip.provider")

  override fun getIcon(value: String): Icon {
    return PythonPsiApiIcons.Python
  }

  override fun getHelpIcon(): Icon {
    return PythonPsiApiIcons.Python
  }

  override fun getDefaultCommands(): Sequence<String> {
    return PIP_COMMANDS
  }

  override fun getPackageManager(dataContext: DataContext): PythonPackageManager? {
    val pythonSdk = getSdk(dataContext) ?: return null
    return PythonPackageManager.forSdk(dataContext.project, pythonSdk)
  }

  override fun getPackageRepository(dataContext: DataContext): PyPackageRepository? {
    return getPackageManager(dataContext)?.repositoryManager?.repositories?.first { it is PyPIPackageRepository }
  }

  override fun getLogCommandType(): CommandType = CommandType.PIP
}

val PIP_COMMANDS = listOf("install", "download", "uninstall", "freeze", "list", "show", "wheel", "hash", "check", "config", "cache",
                          "completion", "help").asSequence()