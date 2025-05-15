// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("CompanionObjectInExtension")

package com.jetbrains.python.inspections.unresolvedReference

import com.intellij.codeInspection.InspectionProfile
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.QualifiedName
import com.intellij.psi.util.parentOfType
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.PyCodeInsightSettings
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.imports.AutoImportHintAction
import com.jetbrains.python.codeInsight.imports.AutoImportQuickFix
import com.jetbrains.python.codeInsight.imports.PythonImportUtils
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.inspections.PyUnresolvedReferenceQuickFixProvider
import com.jetbrains.python.inspections.quickfix.*
import com.jetbrains.python.packaging.PyPackageInstallUtils
import com.jetbrains.python.packaging.PyPackageUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyFromImportStatementImpl
import com.jetbrains.python.psi.impl.PyImportElementImpl
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher
import com.jetbrains.python.psi.impl.references.PyImportReference
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.sdk.PythonSdkUtil

/**
 * Marks references that fail to resolve.
 */
class PyUnresolvedReferencesInspection : PyUnresolvedReferencesInspectionBase() {
  @JvmField
  var ignoredIdentifiers: List<String> = ArrayList()

  override fun createVisitor(holder: ProblemsHolder, session: LocalInspectionToolSession): PyUnresolvedReferencesVisitor =
    Visitor(holder,
            ignoredIdentifiers,
            PyInspectionVisitor.getContext(session),
            PythonLanguageLevelPusher.getLanguageLevelForFile(session.file))

  override fun getOptionsPane(): OptPane = OptPane.pane(
    OptPane.stringList("ignoredIdentifiers",
                       PyPsiBundle.message("INSP.unresolved.refs.ignore.references.label")))

  private class Visitor(
    holder: ProblemsHolder,
    ignoredIdentifiers: List<String>,
    context: TypeEvalContext,
    languageLevel: LanguageLevel,
  ) : PyUnresolvedReferencesVisitor(holder, ignoredIdentifiers, context, languageLevel) {

    override fun getInstallPackageQuickFixes(
      node: PyElement,
      reference: PsiReference,
      refName: String,
    ): List<LocalQuickFix> {
      if (reference !is PyImportReference) {
        return emptyList()
      }

      //Ignore references in the second part of the 'from ... import ...' expression
      val fromImport = node.parentOfType<PyFromImportStatementImpl>()
      if (fromImport != null) {
        val importSection = node.parentOfType<PyImportElementImpl>()
        if (importSection != null) {
          return emptyList()
        }
      }

      val qname = QualifiedName.fromDottedString(refName)
      val components = qname.components
      if (components.isEmpty()) {
        return emptyList()
      }

      val packageName = components[0]
      val module = ModuleUtilCore.findModuleForPsiElement(node)
      val sdk = PythonSdkUtil.findPythonSdk(module)
      if (module == null || sdk == null || !PyPackageUtil.packageManagementEnabled(sdk, false, true)) {
        return emptyList()
      }


      val pyPackage = PyPackageInstallUtils.offeredPackageForNotFoundModule(module.project, sdk, packageName)
      val packageCandidates = listOfNotNull(pyPackage)
      return packageCandidates.map { pkg: String -> InstallPackageQuickFix(pkg) }
    }

    override fun getInstallAllPackagesQuickFix(): InstallAllPackagesQuickFix {
      return InstallAllPackagesQuickFix(myUnresolvedRefs.map { it.refName }.distinct())
    }

    override fun getAddIgnoredIdentifierQuickFixes(qualifiedNames: List<QualifiedName>): List<LocalQuickFix> {
      val result: MutableList<LocalQuickFix> = ArrayList(2)
      if (qualifiedNames.size == 1) {
        val qualifiedName = qualifiedNames[0]
        result.add(AddIgnoredIdentifierQuickFix(qualifiedName, false))
        if (qualifiedName.componentCount > 1) {
          result.add(AddIgnoredIdentifierQuickFix(qualifiedName.removeLastComponent(), true))
        }
      }
      return result
    }

    override fun getImportStatementQuickFixes(element: PsiElement): List<LocalQuickFix> {
      val importStatementBase = PsiTreeUtil.getParentOfType(element,
                                                            PyImportStatementBase::class.java)
      if ((importStatementBase != null) && GenerateBinaryStubsFix.isApplicable(importStatementBase)) {
        return GenerateBinaryStubsFix.generateFixes(importStatementBase)
      }

      return emptyList()
    }

    override fun getAutoImportFixes(node: PyElement, reference: PsiReference, element: PsiElement): List<LocalQuickFix> {
      // look in other imported modules for this whole name
      if (!PythonImportUtils.isImportable(element)) {
        return emptyList()
      }
      val file = InjectedLanguageManager.getInstance(node.project).getTopLevelFile(node)
      if (file !is PyFile) {
        return emptyList()
      }
      val result: MutableList<LocalQuickFix> = ArrayList()
      val importFix = PythonImportUtils.proposeImportFix(node, reference)
      if (importFix != null) {
        if (!suppressHintForAutoImport(node, importFix) && PyCodeInsightSettings.getInstance().SHOW_IMPORT_POPUP) {
          val autoImportHintAction = AutoImportHintAction(importFix)
          result.add(autoImportHintAction)
        }
        else {
          result.add(importFix)
        }
        if (ScopeUtil.getScopeOwner(node) is PyFunction) {
          result.add(importFix.forLocalImport())
        }
      }

      val referencedName = if (node is PyReferenceExpression && !node.isQualified) node.referencedName else null
      val pythonSdk = PythonSdkUtil.findPythonSdk(node)
      if (referencedName != null && pythonSdk != null) {
        ContainerUtil.addIfNotNull(result, createInstallAndImportQuickFix(node.project, pythonSdk, referencedName, null))
        val realPackageName = PY_COMMON_IMPORT_ALIASES[referencedName]
        if (realPackageName != null) {
          ContainerUtil.addIfNotNull(result, createInstallAndImportQuickFix(node.project, pythonSdk, realPackageName, referencedName))
        }
      }

      return result
    }

    override fun getPluginQuickFixes(fixes: List<LocalQuickFix>, reference: PsiReference) {
      for (provider in PyUnresolvedReferenceQuickFixProvider.EP_NAME.extensionList) {
        provider.registerQuickFixes(reference, fixes)
      }
    }
  }

  companion object {
    private val SHORT_NAME_KEY = Key.create<PyUnresolvedReferencesInspection>(PyUnresolvedReferencesInspection::class.java.simpleName)

    fun getInstance(element: PsiElement?): PyUnresolvedReferencesInspection? {
      element ?: return null

      val inspectionProfile: InspectionProfile = InspectionProjectProfileManager.getInstance(element.project).currentProfile
      return inspectionProfile.getUnwrappedTool(SHORT_NAME_KEY.toString(), element) as PyUnresolvedReferencesInspection?
    }

    private fun createInstallAndImportQuickFix(project: Project, pythonSdk: Sdk, packageName: String, asName: String?): LocalQuickFix? {
      return if (PyPackageInstallUtils.checkShouldToInstall(project, pythonSdk, packageName))
        InstallAndImportPackageQuickFix(packageName, asName)
      else
        null
    }

    private fun suppressHintForAutoImport(node: PyElement, importFix: AutoImportQuickFix): Boolean {
      // if the context doesn't look like a function call and we only found imports of functions, suggest auto-import
      // as a quickfix but no popup balloon (PY-2312)
      if (!isCall(node) && importFix.hasOnlyFunctions()) {
        return true
      }
      // if we're in a class context and the class defines a variable with the same name, offer auto-import only as quickfix,
      // not as popup
      val containingClass = PsiTreeUtil.getParentOfType(node, PyClass::class.java)
      return containingClass != null && (containingClass.findMethodByName(importFix.nameToImport, true, null) != null ||
                                         containingClass.findInstanceAttribute(importFix.nameToImport, true) != null)
    }

    private fun isCall(node: PyElement): Boolean {
      val callExpression = PsiTreeUtil.getParentOfType(node,
                                                       PyCallExpression::class.java)
      return callExpression != null && node === callExpression.callee
    }
  }
}
