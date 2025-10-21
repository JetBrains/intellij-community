// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.namespacePackages

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.util.PlatformUtils
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyLanguageFacade
import com.jetbrains.python.psi.LanguageLevel

class PyMarkAsNamespacePackageAction : AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    presentation.isEnabled = false
    presentation.isVisible = false

    val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
    if (virtualFiles.isEmpty()) return
    val project = e.project ?: return
    if (virtualFiles.any { PyLanguageFacade.INSTANCE.getEffectiveLanguageLevel(project, it).isOlderThan(LanguageLevel.PYTHON34) }) {
      return
    }

    val module = e.getData(PlatformCoreDataKeys.MODULE) ?: return
    val service = PyNamespacePackagesService.getInstance(module)
    if (!PyNamespacePackagesService.isEnabled()) return
    presentation.isVisible = true

    presentation.icon = AllIcons.Nodes.Package
    when {
      virtualFiles.all { service.canBeMarked(it) } -> {
        presentation.isEnabled = true
        presentation.text = if (PlatformUtils.isPyCharm()) {
          PyBundle.message("python.namespace.package.folder")
        }
        else {
          PyBundle.message("python.python.namespace.package.folder")
        }
      }
      virtualFiles.all { service.isMarked(it) } -> {
        presentation.isEnabled = true
        presentation.text = if (PlatformUtils.isPyCharm()) {
          PyBundle.message("python.unmark.as.namespace.package")
        }
        else {
          PyBundle.message("python.unmark.as.python.namespace.package")
        }
      }
      else -> {
        presentation.isEnabled = false
        presentation.text = if (PlatformUtils.isPyCharm()) {
          PyBundle.message("python.namespace.package.folder")
        }
        else {
          PyBundle.message("python.python.namespace.package.folder")
        }
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val module = e.getData(PlatformCoreDataKeys.MODULE) ?: return
    val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
    val service = PyNamespacePackagesService.getInstance(module)
    virtualFiles.forEach { service.toggleMarkingAsNamespacePackage(it) }
  }
}