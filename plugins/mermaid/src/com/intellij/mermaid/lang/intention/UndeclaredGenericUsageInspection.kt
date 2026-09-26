// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.intention

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.psi.MermaidClassDiagramIdentifier
import com.intellij.mermaid.lang.psi.MermaidClassDiagramIdentifierDeclarationHolder
import com.intellij.mermaid.lang.psi.MermaidClassDiagramIdentifierHolder
import com.intellij.mermaid.lang.psi.MermaidElementFactory
import com.intellij.mermaid.lang.psi.MermaidFile
import com.intellij.mermaid.lang.psi.MermaidGeneric
import com.intellij.mermaid.lang.psi.MermaidGenericTypeId
import com.intellij.mermaid.lang.psi.MermaidRecursiveVisitor
import com.intellij.mermaid.lang.psi.identifier
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.parentOfType

class UndeclaredGenericUsageInspection : LocalInspectionTool() {

  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    if (file !is MermaidFile) return null

    val result = mutableListOf<ProblemDescriptor>()
    file.accept(GenericUsagesProblemsCollector(manager, isOnTheFly, result))

    return result.toTypedArray()
  }

  override fun runForWholeFile(): Boolean {
    return true
  }

  private class GenericUsagesProblemsCollector(
    private val manager: InspectionManager,
    private val isOnTheFly: Boolean,
    private val result: MutableList<ProblemDescriptor>
  ) : MermaidRecursiveVisitor() {
    private val declaredGenerics = mutableSetOf<String>()
    private val declarations = mutableSetOf<String>()

    override fun visitClassDiagramIdentifierHolder(identifierHolder: MermaidClassDiagramIdentifierHolder) {
      visitGenericUsage(identifierHolder.identifier().text, identifierHolder.generic?.genericTypeId)
      super.visitClassDiagramIdentifierHolder(identifierHolder)
    }

    override fun visitClassDiagramIdentifierDeclarationHolder(declarationHolder: MermaidClassDiagramIdentifierDeclarationHolder) {
      val diagramIdentifier = declarationHolder.identifier()
      val genericTypeId = declarationHolder.generic?.genericTypeId
      val id = diagramIdentifier.text
      if (declarations.add(id)) {
        collectGenericDeclaration(id, genericTypeId)
      } else {
        visitGenericUsage(id, genericTypeId)
      }
      super.visitClassDiagramIdentifierDeclarationHolder(declarationHolder)
    }

    private fun collectGenericDeclaration(identifierText: String, genericTypeId: MermaidGenericTypeId?) {
      genericTypeId ?: return
      declaredGenerics.add(identifierText)
    }

    private fun visitGenericUsage(identifierText: String, genericTypeId: MermaidGenericTypeId?) {
      genericTypeId ?: return
      if (identifierText in declaredGenerics) return

      result.add(
        manager.createProblemDescriptor(
          genericTypeId,
          MermaidBundle.message("generic.will.not.be.rendered"),
          isOnTheFly,
          arrayOf(
            RemoveGenericToDeclarationQuickFix(),
            AddGenericToDeclarationQuickFix(),
            RemoveGenericQuickFix(),
          ),
          ProblemHighlightType.WARNING
        )
      )
    }
  }

  class RemoveGenericToDeclarationQuickFix: UndeclaredGenericLocalQuickFix() {
    override fun getFamilyName() = MermaidBundle.message("fix.remove.generic.to.declaration")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      addGenericElement(project, descriptor)
      deleteGenericElement(descriptor)
    }
  }

  class AddGenericToDeclarationQuickFix: UndeclaredGenericLocalQuickFix() {
    override fun getFamilyName() = MermaidBundle.message("fix.add.generic.to.declaration")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      addGenericElement(project, descriptor)
    }
  }

  class RemoveGenericQuickFix: UndeclaredGenericLocalQuickFix() {
    override fun getFamilyName() = MermaidBundle.message("fix.remove.generic")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      deleteGenericElement(descriptor)
    }
  }

  abstract class UndeclaredGenericLocalQuickFix: LocalQuickFix {
    protected fun deleteGenericElement(descriptor: ProblemDescriptor) {
      val genericTypeId = descriptor.psiElement
      val genericElement = genericTypeId.parentOfType<MermaidGeneric>() ?: return
      genericElement.delete()
    }

    protected fun addGenericElement(project: Project, descriptor: ProblemDescriptor) {
      val genericTypeId = descriptor.psiElement
      val genericElement = genericTypeId.parentOfType<MermaidGeneric>() ?: return
      val parent = genericElement.parentOfType<MermaidClassDiagramIdentifierHolder>() ?: return
      val elementId = parent.classDiagramIdentifier

      val (declaration, newParent) = findDeclarationAndParent(genericTypeId.containingFile, elementId) ?: return

      val generic = MermaidElementFactory.createGenericElement(project, genericTypeId.text) ?: return
      newParent.addAfter(generic, declaration)
    }

    private fun findDeclarationAndParent(file: PsiFile, elementId: MermaidClassDiagramIdentifier): Pair<MermaidClassDiagramIdentifier, PsiElement>? {
      return SyntaxTraverser.psiTraverser(file)
        .asSequence()
        .filterIsInstance<MermaidClassDiagramIdentifierHolder>()
        .firstNotNullOfOrNull { collectDeclaration(it.classDiagramIdentifier, elementId) }
    }

    private fun collectDeclaration(
      diagramIdentifier: MermaidClassDiagramIdentifier,
      elementId: MermaidClassDiagramIdentifier
    ): Pair<MermaidClassDiagramIdentifier, PsiElement>? {
      if (diagramIdentifier.textMatches(elementId)) {
        return diagramIdentifier to diagramIdentifier.parent
      }
      return null
    }
  }
}
