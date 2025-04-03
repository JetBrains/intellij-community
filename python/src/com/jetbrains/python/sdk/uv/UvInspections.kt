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
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.findTomlHeader
import com.intellij.python.pyproject.findTomlLiteralsContaining
import com.intellij.python.pyproject.findTomlValueByKey
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.requirements.createNameReq
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.findAmongRoots
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class UvPackageVersionsInspection : LocalInspectionTool() {
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
      val module = guessModule(file) ?: return
      val sdk = PythonSdkUtil.findPythonSdk(module) ?: return

      if (!sdk.isUv || file.name != PY_PROJECT_TOML || file.virtualFile != module.pyProjectTomlBlocking()) {
        return
      }

      val pyProject = PyProjectToml.parse(file.virtualFile.inputStream).getOr { return }
      val uvTool = pyProject.getTool(UvPyProject)
      val outdatedPackages = (PythonPackageManager.forSdk(module.project, sdk) as? UvPackageManager)?.outdatedPackages ?: return

      uvTool.matchOutdatedPackages(module, pyProject, outdatedPackages).forEach { pyReq ->
        listOfNotNull(
          file.findTomlHeader("project")?.findTomlValueByKey("dependencies"),
          file.findTomlHeader("dependency-groups")?.findTomlValueByKey("dev"),
          file.findTomlHeader("tool.uv")?.findTomlValueByKey("dev-dependencies")
        ).forEach { psiArray ->
          psiArray.findTomlLiteralsContaining(pyReq.displayName).forEach { psiLiteral ->
            holder.registerProblem(
              psiLiteral,
              PyBundle.message(
                "python.sdk.inspection.message.version.outdated.latest",
                pyReq.displayName,
                outdatedPackages[pyReq.displayName]!!.version,
                outdatedPackages[pyReq.displayName]!!.latestVersion
              ),
              ProblemHighlightType.WARNING
            )
          }
        }
      }
    }
  }
}