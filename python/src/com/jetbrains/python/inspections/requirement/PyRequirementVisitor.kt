package com.jetbrains.python.inspections.requirement

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyPsiPackageUtil.moduleToPackageName
import com.jetbrains.python.codeInsight.stdlib.PyStdlibUtil
import com.jetbrains.python.inspections.PyInspectionExtension
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.inspections.quickfix.IgnoreRequirementFix
import com.jetbrains.python.inspections.quickfix.PyGenerateRequirementsFileQuickFix
import com.jetbrains.python.inspections.quickfix.PyInstallRequirementsFix
import com.jetbrains.python.packaging.PyPIPackageUtil
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.PyPackageUtil
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.sdk.PySdkProvider
import com.jetbrains.python.sdk.PythonSdkUtil
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class PyRequirementVisitor(
  holder: ProblemsHolder?,
  ignoredPackages: Collection<String>,
  context: TypeEvalContext,
) : PyInspectionVisitor(holder, context) {

  private val myIgnoredPackages: Set<String>?= ignoredPackages.toSet()

  override fun visitPyFile(node: PyFile) {
    val module = ModuleUtilCore.findModuleForPsiElement(node) ?: return
    checkPackagesHaveBeenInstalled(node, module)
  }

  private fun checkPackagesHaveBeenInstalled(file: PsiElement, module: Module) {
    if (isRunningPackagingTasks(module)) return
    val sdk = PythonSdkUtil.findPythonSdk(module) ?: return
    val manager = PythonPackageManager.forSdk(module.project, sdk)

    val unsatisfied = myIgnoredPackages?.let { findUnsatisfiedRequirements(module, manager, it) }.orEmpty()
    if (unsatisfied.isEmpty()) return

    val requirementsList = PyPackageUtil.requirementsToString(unsatisfied)
    val message = PyPsiBundle.message(
      REQUIREMENT_NOT_SATISFIED,
      requirementsList,
      unsatisfied.size)

    val quickFixes = mutableListOf<LocalQuickFix>().apply {
      val providedFix = PySdkProvider.EP_NAME.extensionList
        .asSequence()
        .mapNotNull { it.createInstallPackagesQuickFix(module) }
        .firstOrNull()

      add(providedFix ?: PyInstallRequirementsFix(null, module, sdk, unsatisfied))
      add(IgnoreRequirementFix(unsatisfied.mapTo(mutableSetOf()) { it.name }))
    }

    registerProblem(
      file,
      message,
      ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
      null,
      *quickFixes.toTypedArray()
    )
  }

  override fun visitPyFromImportStatement(node: PyFromImportStatement) {
    node.importSource?.let { checkPackageNameInRequirements(it) }
  }

  override fun visitPyImportStatement(node: PyImportStatement) {
    node.importElements.mapNotNull { it.importReferenceExpression }.forEach { checkPackageNameInRequirements(it) }
  }

  private fun findUnsatisfiedRequirements(
    module: Module,
    manager: PythonPackageManager,
    ignoredPackages: Set<String?>,
  ): List<PyRequirement> {
    val requirements = getRequirements(module) ?: return emptyList()
    val serviceScope = PyPackagingToolWindowService.getInstance(module.project).serviceScope

    serviceScope.launch {
      manager.reloadPackages()
    }

    val installedPackages = manager.installedPackages.toPyPackages()
    val modulePackages = collectPackagesInModule(module)

    return requirements.filter { requirement ->
      isRequirementUnsatisfied(requirement, ignoredPackages, installedPackages, modulePackages)
    }
  }

  private fun isRequirementUnsatisfied(
    requirement: PyRequirement,
    ignoredPackages: Set<String?>,
    installedPackages: List<PyPackage>,
    modulePackages: List<PyPackage>,
  ): Boolean =
    !ignoredPackages.contains(requirement.name) &&
    requirement.match(installedPackages) == null &&
    requirement.match(modulePackages) == null

  private fun List<PythonPackage>.toPyPackages(): List<PyPackage> = map { PyPackage(it.name, it.version) }

  private fun getRequirements(module: Module): List<PyRequirement>? =
    PyPackageUtil.getRequirementsFromTxt(module) ?: PyPackageUtil.findSetupPyRequires(module)

  private fun collectPackagesInModule(module: Module): List<PyPackage> {
    return PyUtil.getSourceRoots(module).flatMap { srcRoot ->
      VfsUtil.getChildren(srcRoot).filter { file ->
        METADATA_EXTENSIONS.contains(file.extension)
      }.mapNotNull { metadataFile ->
        parsePackageNameAndVersion(metadataFile.nameWithoutExtension)
      }
    }
  }

  private fun parsePackageNameAndVersion(nameWithoutExtension: String): PyPackage? {
    val components = splitNameIntoComponents(nameWithoutExtension)
    return if (components.size >= 2) PyPackage(components[0], components[1]) else null
  }

  private fun checkPackageNameInRequirements(importedExpression: PyQualifiedExpression) {
    if (PyInspectionExtension.EP_NAME.extensionList.any { it.ignorePackageNameInRequirements(importedExpression) }) return

    val packageReferenceExpression = PyPsiUtils.getFirstQualifier(importedExpression)
    val packageName = packageReferenceExpression.name ?: return

    if (isIgnoredOrStandardPackage(packageName)) return

    val possiblePyPIPackageNames = moduleToPackageName(packageName, EMPTY_STRING)
    if (!ApplicationManager.getApplication().isUnitTestMode() && !isPackageInPyPI(listOf(packageName, possiblePyPIPackageNames))) return

    val module = ModuleUtilCore.findModuleForPsiElement(packageReferenceExpression) ?: return
    val sdk = PythonSdkUtil.findPythonSdk(module) ?: return
    val packageManager = PythonPackageManager.forSdk(module.project, sdk)
    val requirements = getRequirementsInclTransitive(packageManager, module)

    if (isPackageSatisfied(packageName, possiblePyPIPackageNames, requirements) || isLocalModule(packageReferenceExpression, module)) return

    registerProblem(
      packageReferenceExpression,
      PyPsiBundle.message(
        PACKAGE_NOT_LISTED,
        packageName
      ),
      ProblemHighlightType.WEAK_WARNING,
      null,
      PyGenerateRequirementsFileQuickFix(module),
      IgnoreRequirementFix(setOf(packageName))
    )
  }

  private fun isIgnoredOrStandardPackage(packageName: String): Boolean =
    myIgnoredPackages?.contains(packageName) == true ||
    packageName == PyPackageUtil.SETUPTOOLS ||
    PyStdlibUtil.getPackages()?.contains(packageName) == true

  private fun isPackageInPyPI(packageNames: List<String>): Boolean =
    packageNames.any { PyPIPackageUtil.INSTANCE.isInPyPI(it) }

  private fun isPackageSatisfied(
    packageName: String,
    possiblePyPIPackageNames: String,
    requirements: Collection<PyRequirement>,
  ): Boolean =
    requirements.map { it.name.variations() }.flatten().contains(packageName) ||
    requirements.map { it.name.variations() }.flatten().contains(possiblePyPIPackageNames)

  private fun String.variations() = listOf(
    this,
    this.replace("_", "-"),
    this.replace("-", "_")
  )

  private fun isLocalModule(packageReferenceExpression: PyExpression, module: Module): Boolean {
    val reference = packageReferenceExpression.reference ?: return false
    val element = reference.resolve() ?: return false

    if (element is PsiDirectory) {
      return ModuleUtilCore.moduleContainsFile(module, element.virtualFile, false)
    }

    val file = element.containingFile ?: return false
    val virtualFile = file.virtualFile ?: return false
    return ModuleUtilCore.moduleContainsFile(module, virtualFile, false)
  }

  private fun getRequirementsInclTransitive(packageManager: PythonPackageManager, module: Module): Set<PyRequirement> {
    val requirements = getListedRequirements(module)
    if (requirements.isEmpty()) return emptySet()

    val packages = packageManager.installedPackages
    return HashSet(getTransitiveRequirements(packages.toPyPackages(), requirements, HashSet())) + requirements
  }


  private fun getListedRequirements(module: Module): List<PyRequirement> {
    val requirements = getRequirements(module).orEmpty()
    val extrasRequirements = getExtrasRequirements(module)

    return if (requirements.isEmpty() && extrasRequirements.isEmpty()) {
      emptyList()
    }
    else {
      requirements + extrasRequirements
    }
  }

  private fun getExtrasRequirements(module: Module): List<PyRequirement> =
    PyPackageUtil.findSetupPyExtrasRequire(module)
      ?.values
      ?.flatten()
      .orEmpty()

  private fun getTransitiveRequirements(
    packages: List<PyPackage>,
    requirements: Collection<PyRequirement>,
    visited: MutableSet<PyPackage>,
  ): Set<PyRequirement> {
    val result: MutableSet<PyRequirement> = HashSet()

    for (requirement in requirements) {
      val myPackage = requirement.match(packages)
      if (myPackage != null && visited.add(myPackage)) {
        result.addAll(getTransitiveRequirements(packages, myPackage.requirements, visited))
      }
    }

    return result
  }

  private fun isRunningPackagingTasks(module: Module): Boolean {
    val value = module.getUserData(PythonPackageManager.RUNNING_PACKAGING_TASKS)
    return value != null && value
  }

  companion object {
    private const val PACKAGE_NOT_LISTED = "INSP.requirements.package.containing.module.not.listed.in.project.requirements"
    private const val REQUIREMENT_NOT_SATISFIED =  "INSP.requirements.package.requirements.not.satisfied"
    private val METADATA_EXTENSIONS = setOf("egg-info", "dist-info")
    private const val EMPTY_STRING = ""

    fun splitNameIntoComponents(name: String): Array<String> = name.split("-".toRegex(), limit = 3).toTypedArray()
  }
}