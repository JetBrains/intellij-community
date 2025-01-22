// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.inspections.requirement.PyRequirementVisitor
import com.jetbrains.python.psi.PyFile
import one.util.streamex.StreamEx

class PyPackageRequirementsInspection() : PyInspection() {

  private val ignoredPackages: MutableList<String> = mutableListOf()

  fun getIgnoredPackages(): List<String> = ignoredPackages

  fun setIgnoredPackages(packages: Set<String>) {
    ignoredPackages.clear()
    ignoredPackages.addAll(packages.distinct())
  }

  override fun getOptionsPane(): OptPane {
    return OptPane.pane(OptPane.stringList("ignoredPackages", PyPsiBundle.message(IGNORED_PACKAGES)))
  }

  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor {
    return if (holder.file !is PyFile && !isPythonInTemplateLanguages(holder.file)) {
      PsiElementVisitor.EMPTY_VISITOR
    }
    else {
      PyRequirementVisitor(holder, ignoredPackages, PyInspectionVisitor.getContext(session))
    }
  }

  private fun isPythonInTemplateLanguages(psiFile: PsiFile) =
    StreamEx.of(psiFile.viewProvider.languages)
      .findFirst { it.isKindOf(PythonLanguage.getInstance()) }
      .isPresent

  companion object {
    fun getInstance(element: PsiElement): PyPackageRequirementsInspection? {
      val inspectionProfile = InspectionProjectProfileManager.getInstance(element.project).currentProfile
      val toolName = PyPackageRequirementsInspection::class.java.simpleName
      return inspectionProfile.getUnwrappedTool(toolName, element) as PyPackageRequirementsInspection?
    }
    private const val IGNORED_PACKAGES = "INSP.requirements.ignore.packages.label"
  }
}