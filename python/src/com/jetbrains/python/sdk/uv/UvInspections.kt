// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.findAmongRoots
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable
import kotlin.collections.contains

internal class UvPackageVersionsInspection : LocalInspectionTool() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor {
    return UvFileVisitor(holder, session)
  }

  internal class UvFileVisitor(
    val holder: ProblemsHolder,
    session: LocalInspectionToolSession,
  ) : PsiElementVisitor() {
    @RequiresBackgroundThread
    private fun guessModule(element: PsiElement): Module? {
      return ModuleUtilCore.findModuleForPsiElement(element)
    }

    @RequiresBackgroundThread
    private fun Module.pyProjectTomlBlocking(): VirtualFile? = findAmongRoots(this, PY_PROJECT_TOML)

    @RequiresBackgroundThread
    override fun visitFile(file: PsiFile) {
      val module = guessModule(file)
      if (module == null) {
        return
      }

      val sdk = PythonSdkUtil.findPythonSdk(module)
      if (sdk == null || !sdk.isUv) {
        return
      }

      if (file.virtualFile != module.pyProjectTomlBlocking()) {
        return
      }

      file.children
        .filter { element ->
          (element as? TomlTable)?.header?.key?.text in listOf("dependencies", "dev-dependencies")
        }.flatMap {
          it.children.mapNotNull { line -> line as? TomlKeyValue }
        }.forEach { keyValue ->
          val packageName = keyValue.key.text
          val outdated = (PythonPackageManager.forSdk(
            module.project, sdk) as? UvPackageManager)?.let {
            it.outdatedPackages[packageName]
          }

          if (outdated != null) {
            val message = PyBundle.message("python.sdk.inspection.message.version.outdated.latest",
                                           packageName, outdated.version, outdated.latestVersion)
            holder.registerProblem(keyValue, message, ProblemHighlightType.WARNING)
          }
        }
    }
  }
}