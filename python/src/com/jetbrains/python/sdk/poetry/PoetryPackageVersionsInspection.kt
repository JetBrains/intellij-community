// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.normalizePackageName
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.findAmongRoots
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable

/**
 *  This source code is created by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */

internal class PoetryPackageVersionsInspection : LocalInspectionTool() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor {
    return PoetryFileVisitor(holder, session)
  }

  class PoetryFileVisitor(
    val holder: ProblemsHolder,
    session: LocalInspectionToolSession,
  ) : PsiElementVisitor() {
    @RequiresBackgroundThread
    private fun guessModule(element: PsiElement): Module? {
      return ModuleUtilCore.findModuleForPsiElement(element)
             ?: ModuleManager.getInstance(element.project).modules.let { if (it.size != 1) null else it[0] }
    }

    @RequiresBackgroundThread
    private fun Module.pyProjectTomlBlocking(): VirtualFile? = findAmongRoots(this, PY_PROJECT_TOML)

    @RequiresBackgroundThread
    override fun visitFile(psiFile: PsiFile) {
      val module = guessModule(psiFile) ?: return
      val sdk = PythonSdkUtil.findPythonSdk(module) ?: return
      if (!sdk.isPoetry) return
      if (psiFile.virtualFile != module.pyProjectTomlBlocking()) return
      psiFile.children
        .filter { element ->
          (element as? TomlTable)?.header?.key?.text in listOf("tool.poetry.dependencies", "tool.poetry.dev-dependencies")
        }.flatMap {
          it.children.mapNotNull { line -> line as? TomlKeyValue }
        }.forEach { keyValue ->
          val packageName = normalizePackageName(keyValue.key.text)
          val outdatedVersion = PythonPackageManager.forSdk(module.project, sdk).outdatedPackages[packageName]
          if (outdatedVersion != null) {
            val message = PyBundle.message("python.sdk.inspection.message.version.outdated.latest",
                                           packageName, outdatedVersion.version, outdatedVersion.latestVersion)
            holder.registerProblem(keyValue, message, ProblemHighlightType.WARNING)
          }
        }
    }
  }
}