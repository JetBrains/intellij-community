// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.namespacePackages

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.util.PlatformIcons
import com.jetbrains.python.PyBundle

class PyMarkAsNamespacePackageAction : AnAction() {
  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    presentation.isEnabled = false
    presentation.isVisible = false

    val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
    if (virtualFiles.isEmpty()) return

    val module = e.getData(LangDataKeys.MODULE) ?: return
    val service = PyNamespacePackagesService.getInstance(module)
    if (!PyNamespacePackagesService.isEnabled()) return
    presentation.isVisible = true

    presentation.icon = PlatformIcons.PACKAGE_ICON
    when {
      virtualFiles.all { service.canBeMarked(it) } -> {
        presentation.isEnabled = true
        presentation.text = PyBundle.message("python.namespace.package.folder")
      }
      virtualFiles.all { service.isMarked(it) } -> {
        presentation.isEnabled = true
        presentation.text = PyBundle.message("python.unmark.as.namespace.package")
      }
      else -> {
        presentation.isEnabled = false
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val module = e.getData(LangDataKeys.MODULE) ?: return
    val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
    val service = PyNamespacePackagesService.getInstance(module)
    virtualFiles.forEach { service.toggleMarkingAsNamespacePackage(it) }
  }
}