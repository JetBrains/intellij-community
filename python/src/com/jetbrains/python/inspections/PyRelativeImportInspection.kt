// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.namespacePackages.PyNamespacePackagesService
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.types.TypeEvalContext

class PyRelativeImportInspection : PyInspection() {
  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor {
    if (!PyNamespacePackagesService.isEnabled() ||
        LanguageLevel.forElement(holder.file).isOlderThan(LanguageLevel.PYTHON34)) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    return Visitor(holder, PyInspectionVisitor.getContext(session))
  }

  private class Visitor(holder: ProblemsHolder, context: TypeEvalContext) : PyInspectionVisitor(holder, context) {
    override fun visitPyFromImportStatement(node: PyFromImportStatement) {
      val directory = node.containingFile?.containingDirectory ?: return
      if (node.relativeLevel > 0 && !PyUtil.isExplicitPackage(directory) && !isInsideOrdinaryPackage(directory)) {
        handleRelativeImportNotInsidePackage(node, directory)
      }
    }

    private fun isInsideOrdinaryPackage(directory: PsiDirectory): Boolean {
      var curDir: PsiDirectory? = directory
      while (curDir != null) {
        if (PyUtil.isOrdinaryPackage(curDir)) return true
        curDir = curDir.parentDirectory
      }
      return false
    }

    private fun handleRelativeImportNotInsidePackage(node: PyFromImportStatement, directory: PsiDirectory) {
      val fixes = mutableListOf<LocalQuickFix>()
      getMarkAsNamespacePackageQuickFix(directory) ?.let { fixes.add(it) }
      if (node.relativeLevel == 1) {
        fixes.add(PyChangeToSameDirectoryImportQuickFix())
      }
      val message = PyPsiBundle.message("INSP.relative.import.relative.import.outside.package")
      registerProblem(node, message, *fixes.toTypedArray())
    }

    private fun getMarkAsNamespacePackageQuickFix(directory: PsiDirectory): PyMarkAsNamespacePackageQuickFix? {
      val module = ModuleUtilCore.findModuleForPsiElement(directory) ?: return null

      var curDir: PsiDirectory? = directory
      while (curDir != null) {
        val virtualFile = curDir.virtualFile
        if (PyUtil.isRoot(curDir)) return null

        val parentDir = curDir.parentDirectory
        if (parentDir != null && (PyUtil.isRoot(parentDir) || PyUtil.isOrdinaryPackage(parentDir))) {
          return PyMarkAsNamespacePackageQuickFix(module, virtualFile)
        }

        curDir = parentDir
      }

      return null
    }
  }

  private class PyMarkAsNamespacePackageQuickFix(val module: Module, val directory: VirtualFile) : LocalQuickFix {
    override fun getFamilyName(): String = PyBundle.message("QFIX.mark.as.namespace.package", directory.name)

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val document = PsiDocumentManager.getInstance(project).getDocument(descriptor.psiElement.containingFile)
      val undoableAction = object: BasicUndoableAction(document) {
        override fun undo() {
          PyNamespacePackagesService.getInstance(module).toggleMarkingAsNamespacePackage(directory)
        }

        override fun redo() {
          PyNamespacePackagesService.getInstance(module).toggleMarkingAsNamespacePackage(directory)
        }
      }
      undoableAction.redo()
      UndoManager.getInstance(project).undoableActionPerformed(undoableAction)
    }

    override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
      // The quick fix updates a directory's properties in the project structure, nothing changes in the current file
      return IntentionPreviewInfo.EMPTY
    }
  }

  private class PyChangeToSameDirectoryImportQuickFix : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName(): String = PyBundle.message("QFIX.change.to.same.directory.import")

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
      val oldImport = element as? PyFromImportStatement ?: return
      assert(oldImport.relativeLevel == 1)
      val qualifier = oldImport.importSource
      if (qualifier != null) {
        val possibleDot = updater.getWritable(PsiTreeUtil.prevVisibleLeaf(qualifier))
        assert(possibleDot != null && possibleDot.node.elementType == PyTokenTypes.DOT)
        possibleDot?.delete()
      }
      else {
        replaceByImportStatements(oldImport)
      }
    }

    private fun replaceByImportStatements(oldImport: PyFromImportStatement) {
      val project = oldImport.project
      val generator = PyElementGenerator.getInstance(project)
      val names = oldImport.importElements.map { it.text }
      if (names.isEmpty()) return
      val langLevel = LanguageLevel.forElement(oldImport)
      for (name in names.reversed()) {
        val newImport = generator.createImportStatement(langLevel, name, null)
        oldImport.parent.addAfter(newImport, oldImport)
      }
      oldImport.delete()
    }
  }
}