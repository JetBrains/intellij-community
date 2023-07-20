// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pyproject

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.isInstalled
import com.jetbrains.python.sdk.pythonSdk
import org.toml.lang.psi.*
import org.toml.lang.psi.ext.name

class PyprojectPackageInspection : LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : TomlVisitor() {
      override fun visitLiteral(element: TomlLiteral) {
        if (element.parent is TomlArray && TOML_STRING_LITERALS.contains(element.firstChild.elementType)) {
          if (element.parentOfType<TomlKeyValue>()?.key?.name == "dependencies") {
            val elementText = element.text.removeSurrounding("'").removeSurrounding("\"")
            val requirement = PyRequirementParser.fromLine(elementText) ?: return

            val module = ModuleUtilCore.findModuleForPsiElement(element.originalElement) ?: return
            val packageManager = PythonPackageManager.forSdk(element.project, module.pythonSdk ?: return)

            if (!packageManager.isInstalled(requirement.name)) {
              holder.registerProblem(element, PyBundle.message("python.pyproject.package.not.installed", requirement.name), PyInstallPackageQuickFix(requirement.name), PyInstallProjectAsEditableQuickfix())
            }
          }
        }
      }
    }
  }


}