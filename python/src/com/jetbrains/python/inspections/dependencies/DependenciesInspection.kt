// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.dependencies

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.conda.ExportDependenciesQuickFix
import com.jetbrains.python.inspections.quickfix.UpdateLockedDependenciesQuickFix
import com.jetbrains.python.packaging.NonModulePackageName
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.psi.injectionParent
import com.jetbrains.python.requirements.getPythonSdk
import com.jetbrains.python.requirements.inspections.quickfixes.InstallAllRequirementsQuickFix
import com.jetbrains.python.requirements.inspections.quickfixes.InstallRequirementQuickFix
import com.jetbrains.python.requirements.inspections.quickfixes.UpdateAllRequirementQuickFix
import com.jetbrains.python.requirements.inspections.quickfixes.UpdateRequirementQuickFix
import org.jetbrains.annotations.ApiStatus

/**
 * Combined inspection for two distinct issues that both target the same dependencies scope
 * and share the same SDK / package-manager lookup, so they're folded into one inspection
 * rather than two:
 *
 *  - A requirement names a package that is **not installed** in the active interpreter →
 *    "Package <name> is not installed", with quick fixes [InstallRequirementQuickFix] and
 *    [InstallAllRequirementsQuickFix].
 *  - A requirement names a package that **is installed but outdated** (a newer version is
 *    available in the configured repository) → "Requirement <name>=<v>, latest is <v>", with
 *    quick fixes [UpdateRequirementQuickFix] and [UpdateAllRequirementQuickFix].
 *
 * This is conceptually a file-level inspection: it analyzes a whole dependency file against 
 * the active interpreter. The visitor is therefore driven only for the file element resolves
 * the SDK and package manager once, instead of re-resolving them for every PSI element.
 *
 * An inspection for a specific PSI file should be specified via [DependenciesPsiProvider]
 * extension point. For an example, refer to
 * [com.jetbrains.python.requirements.inspections.tools.RequirementsDependenciesPsiProvider].
 */
@ApiStatus.Internal
class DependenciesInspection : LocalInspectionTool() {
  override fun isAvailableForFile(file: PsiFile): Boolean {
    if (DependenciesPsiProviderData.classes.none { it.isInstance(file) }) {
      return false
    }

    val sdk = getPythonSdk(file) ?: return false
    val packageManager = PythonPackageManager.forSdk(file.project, sdk)

    return file.injectionParent() == null && packageManager.tracksDependencyFile(file)
  }
  
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor {
    val sdk = getPythonSdk(session.file) ?: return PsiElementVisitor.EMPTY_VISITOR
    val packageManager = PythonPackageManager.forSdk(session.file.project, sdk)
    return Visitor(holder, packageManager, InjectedLanguageManager.getInstance(session.file.project))
  }

  override fun isDumbAware(): Boolean = true

  private class Visitor(
    private val holder: ProblemsHolder,
    private val packageManager: PythonPackageManager,
    private val injectedLanguageManager: InjectedLanguageManager,
  ) : PsiElementVisitor() {
    override fun visitFile(rootFile: PsiFile) {
      val dependencyMap = mutableMapOf<PyRequirement, PsiElement>()

      PsiTreeUtil.processElements(rootFile) { element ->
        val resolvedFile = resolvePsiFile(injectedLanguageManager, element)
        val file = when (resolvedFile) {
          is ResolvedPsiFile.File -> resolvedFile.file
          is ResolvedPsiFile.InjectedFile -> resolvedFile.file
          ResolvedPsiFile.NonFile -> return@processElements true
        }
        val eligibleProviders = DependenciesPsiProviderData.dependenciesForFile(file) ?: return@processElements true

        for ((provider, dependencies) in eligibleProviders) {
          if (!resolvedFile.isInjected) {
            verifyNonEmptyFile(file, provider, packageManager)
          }

          for ((pyRequirement, psiElement) in dependencies) {
            dependencyMap[pyRequirement] = if (!resolvedFile.isInjected) psiElement else element
          }
        }

        true
      }

      packageManager.verifyPackageManager(dependencyMap)
    }

    private fun verifyNonEmptyFile(psiFile: PsiFile, provider: DependenciesPsiProvider<*>, packageManager: PythonPackageManager) {
      if (!psiFile.text.isNullOrBlank()) {
        return
      }

      val fixes =
        packageManager
          .dependenciesExporter
          ?.let { arrayOf(ExportDependenciesQuickFix(it)) }
        ?: emptyArray()
      val inspectionMessage = provider.emptyFileInspectionMessage ?: return

      holder.registerProblem(
        psiFile,
        inspectionMessage,
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        *fixes,
      )
    }

    private fun PythonPackageManager.verifyPackageManager(dependencyMap: DependencyMap) {
      val installedPackages = listInstalledPackagesSnapshot()
      val outdatedPackages = listOutdatedPackagesSnapshot()

      val notInstalled = mutableListOf<Pair<PyRequirement, PsiElement>>()
      val outdated = mutableListOf<Pair<PythonOutdatedPackage, PsiElement>>()
      val reservedNames = NonModulePackageName.moduleNames(project)

      for ((pyRequirement, psiElement) in dependencyMap) {
        if (pyRequirement.name in reservedNames) {
          continue
        }

        val installedPackage = installedPackages.firstOrNull { it.name == pyRequirement.name }

        if (installedPackage == null) {
          notInstalled += pyRequirement to psiElement
        }
        else {
          val info = outdatedPackages[pyRequirement.name]

          if (info != null && info.latestVersion != installedPackage.version) {
            outdated += PythonOutdatedPackage(info.name, installedPackage.version, info.latestVersion) to psiElement
          }
        }
      }

      populateNotInstalledProblems(notInstalled, updateLockedAction() != null)
      populateOutdatedProblems(outdated)
    }

    private fun PythonPackageManager.populateNotInstalledProblems(
      notInstalled: List<Pair<PyRequirement, PsiElement>>,
      useUpdateLockFix: Boolean,
    ) {
      if (notInstalled.isEmpty()) {
        return
      }

      val updateLockedDependenciesQuickFix =
        if (useUpdateLockFix) {
          UpdateLockedDependenciesQuickFix(this)
        }
        else {
          null
        }

      val installAllQuickFix =
        if (!useUpdateLockFix && notInstalled.size > 1) {
          InstallAllRequirementsQuickFix(notInstalled.map { it.first })
        }
        else {
          null
        }

      for ((pyRequirement, psiElement) in notInstalled) {
        val installSingleQuickFix =
          if (!useUpdateLockFix) {
            InstallRequirementQuickFix(pyRequirement)
          }
          else {
            null
          }

        val fixes =
          listOfNotNull(installSingleQuickFix, installAllQuickFix, updateLockedDependenciesQuickFix)
            .toTypedArray<LocalQuickFix>()

        if (fixes.isNotEmpty()) {
          holder.registerProblem(
            psiElement,
            PyBundle.message("INSP.dependencies.package.not.installed", pyRequirement.name),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            *fixes
          )
        }
      }
    }

    private fun populateOutdatedProblems(outdated: List<Pair<PythonOutdatedPackage, PsiElement>>) {
      if (outdated.isEmpty()) return

      val updateAllQuickFix = if (outdated.size > 1) {
        UpdateAllRequirementQuickFix(outdated.map { it.first })
      }
      else {
        null
      }

      for ((pythonOutdatedPackage, psiElement) in outdated) {
        val description = PyBundle.message(
          "python.sdk.inspection.message.version.outdated.latest",
          pythonOutdatedPackage.name,
          pythonOutdatedPackage.version,
          pythonOutdatedPackage.latestVersion,
        )
        val fixes =
          listOfNotNull(
            UpdateRequirementQuickFix(pythonOutdatedPackage),
            updateAllQuickFix
          ).toTypedArray()

        holder.registerProblem(
          psiElement,
          description,
          ProblemHighlightType.WEAK_WARNING,
          *fixes,
        )
      }
    }
  }
}
