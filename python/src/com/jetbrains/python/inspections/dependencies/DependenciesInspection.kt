// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.dependencies

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.HintedPsiElementVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
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
 * This is conceptually a file-level inspection: it analyses a whole dependency file (or an
 * injected requirements fragment) against the active interpreter. The visitor is therefore
 * driven only for the file element (see [HintedPsiElementVisitor]) and resolves the SDK and
 * package manager once, instead of re-resolving them for every PSI element.
 *
 * An inspection for a specific PSI file should be specified via [DependenciesInspectionProvider]
 * extension point. For an example, refer to
 * [com.jetbrains.python.requirements.inspections.tools.RequirementsDependenciesInspectionProvider].
 */
@ApiStatus.Internal
class DependenciesInspection : LocalInspectionTool() {
  override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor = object : PsiElementVisitor(), HintedPsiElementVisitor {
    override fun getHintPsiElements(): List<Class<*>> = DependenciesInspectionProviderData.providers.map { it.`class` }

    override fun visitElement(element: PsiElement) {
      val psiFile = element as? PsiFile ?: return
      val sdk = getPythonSdk(psiFile)?.takeIf { !it.isReadOnly } ?: return
      val packageManager = PythonPackageManager.forSdk(psiFile.project, sdk)

      // The dependency file this PSI belongs to: for an injected requirements fragment (pyproject.toml's
      // [project].dependencies, environment.yml's pip section) that's the host file; otherwise the file
      // itself. Inspect only when the interpreter's package manager actually tracks that file in its
      // cached dependency-file tree (the root plus, e.g., uv workspace members), not merely a file that
      // shares the name. The cache is seeded only after installed packages load, so this also means the
      // data we compare against is ready.
      val dependencyFile = psiFile.injectionParent()?.containingFile ?: psiFile
      if (!packageManager.tracksDependencyFile(dependencyFile)) {
        return
      }

      val (provider, dependencies) = DependenciesInspectionProviderData.providers.firstNotNullOfOrNull { provider ->
        provider.getDependencies(psiFile)?.let { provider to it }
      } ?: return

      if (psiFile.injectionParent() == null) {
        verifyNonEmptyFile(psiFile, provider, packageManager)
      }

      packageManager.verifyPackageManager(dependencies)
    }

    private fun verifyNonEmptyFile(psiFile: PsiFile, provider: DependenciesInspectionProvider<*>, packageManager: PythonPackageManager) {
      if (!psiFile.text.isNullOrBlank()) {
        return
      }

      val dependenciesExporter = packageManager.dependenciesExporter
      val fixes = dependenciesExporter?.let { arrayOf(ExportDependenciesQuickFix(it)) } ?: emptyArray()

      holder.registerProblem(
        psiFile,
        provider.emptyFileInspectionMessage,
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        *fixes,
      )
    }

    private fun PythonPackageManager.verifyPackageManager(dependencyMap: DependenciesMap) {
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

      populateNotInstalledProblems(notInstalled,  updateLockedAction() != null)
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
