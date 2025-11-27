// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.interpreter

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.util.PlatformUtils
import com.jetbrains.python.PyPsiBundle

class InterpreterSettingsQuickFix(private val myModule: Module?) : LocalQuickFix {
  override fun getFamilyName(): String = if (PlatformUtils.isPyCharm())
    PyPsiBundle.message("INSP.interpreter.interpreter.settings")
  else
    PyPsiBundle.message("INSP.interpreter.configure.python.interpreter")

  override fun startInWriteAction(): Boolean = false

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    showPythonInterpreterSettings(project, myModule)
  }

  companion object {
    fun showPythonInterpreterSettings(project: Project, module: Module?) {
      val id = "com.jetbrains.python.configuration.PyActiveSdkModuleConfigurable"
      val group = ConfigurableExtensionPointUtil.getConfigurableGroup(project, true)
      if (ConfigurableVisitor.findById(id, listOf(group)) != null) {
        ShowSettingsUtilImpl.showSettingsDialog(project, id, null)
        return
      }

      val settingsService = ProjectSettingsService.getInstance(project)
      if (module == null || justOneModuleInheritingSdk(project, module)) {
        settingsService.openProjectSettings()
      }
      else {
        settingsService.openModuleSettings(module)
      }
    }

    private fun justOneModuleInheritingSdk(project: Project, module: Module): Boolean {
      return ProjectRootManager.getInstance(project).projectSdk == null &&
             ModuleRootManager.getInstance(module).isSdkInherited &&
             ModuleManager.getInstance(project).modules.size < 2
    }
  }
}