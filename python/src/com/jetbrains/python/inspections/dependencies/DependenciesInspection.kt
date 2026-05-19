// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.dependencies

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
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
import com.jetbrains.python.sdk.isReadOnly
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
 * An inspection for a specific PSI file should be specified via [DependenciesInspectionProvider] 
 * extension point. For an example, refer to 
 * [com.jetbrains.python.requirements.inspections.tools.RequirementsDependenciesInspectionProvider].
 */
@ApiStatus.Internal
class DependenciesInspection : LocalInspectionTool() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor = object : PsiElementVisitor() {
    override fun visitElement(element: PsiElement) {
      super.visitElement(element)

      val psiFile = session.file
      val sdk = getPythonSdk(psiFile) ?: return
      var dependencies: DependenciesMap? = null
      lateinit var provider: DependenciesInspectionProvider<*>

      for (entry in DependenciesInspectionProviderData.providers) {
        provider = entry
        entry.getDependencies(element, sdk)?.also {
          dependencies = it
          break
        }
      }

      if (dependencies == null) {
        return
      }

      val packageManager =
        sdk
          .takeIf { !it.isReadOnly }
          ?.let { PythonPackageManager.forSdk(psiFile.project, it) }
          ?.takeIf { it.isInstalledPackagesLoaded }
        ?: return
      val isInjection = psiFile.injectionParent() != null

      if (!isInjection) {
        verifyNonEmptyFile(provider, packageManager)
      }

      packageManager.verifyPackageManager(dependencies, sdk, packageManager)
    }

    fun verifyNonEmptyFile(provider: DependenciesInspectionProvider<*>, packageManager: PythonPackageManager) {
      if (!session.file.text.isNullOrBlank()) {
        return
      }

      val dependenciesExporter = packageManager.dependenciesExporter ?: return
      val emptyFileInspectionMessage = provider.emptyFileInspectionMessage
      
      holder.registerProblem(
        session.file,
        emptyFileInspectionMessage,
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        ExportDependenciesQuickFix(dependenciesExporter),
      )
    }

    fun PythonPackageManager.verifyPackageManager(dependencyMap: DependenciesMap, sdk: Sdk, packageManager: PythonPackageManager) {
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

      populateNotInstalledProblems(notInstalled, sdk, packageManager, updateLockedAction() != null)
      populateOutdatedProblems(outdated)
    }

    private fun populateNotInstalledProblems(
      notInstalled: List<Pair<PyRequirement, PsiElement>>,
      sdk: Sdk,
      packageManager: PythonPackageManager,
      useUpdateLockFix: Boolean,
    ) {
      if (notInstalled.isEmpty()) {
        return
      }

      val updateLockedDependenciesQuickFix =
        if (useUpdateLockFix) {
          UpdateLockedDependenciesQuickFix(sdk, packageManager)
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
        UpdateAllRequirementQuickFix(outdated.map { it.first.name }.toSet())
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
            UpdateRequirementQuickFix(pythonOutdatedPackage.name),
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

  override fun isDumbAware(): Boolean = true
}
