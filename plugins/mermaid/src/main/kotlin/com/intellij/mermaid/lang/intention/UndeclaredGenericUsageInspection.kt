package com.intellij.mermaid.lang.intention

import com.intellij.codeInspection.*
import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.psi.*
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
  ) : MermaidVisitor() {
    private val declaredGenerics = mutableSetOf<String>()
    private val declarations = mutableSetOf<String>()

    override fun visitElement(element: PsiElement) {
      super.visitElement(element)
      element.acceptChildren(this)
    }

    override fun visitMemberStatement(memberStatement: MermaidMemberStatement) {
      visitGenericUsage(memberStatement.classDiagramIdentifier, memberStatement.generic?.genericTypeId)
      super.visitMemberStatement(memberStatement)
    }

    override fun visitAnnotationStatement(annotationStatement: MermaidAnnotationStatement) {
      visitGenericUsage(annotationStatement.classDiagramIdentifier, annotationStatement.generic?.genericTypeId)
      super.visitAnnotationStatement(annotationStatement)
    }

    override fun visitClassDiagramIdentifierDeclarationHolder(declarationHolder: MermaidClassDiagramIdentifierDeclarationHolder) {
      val diagramIdentifier = declarationHolder.classDiagramIdentifier
      val genericTypeId = declarationHolder.generic?.genericTypeId
      val id = diagramIdentifier.text
      if (declarations.add(id)) {
        collectGenericDeclaration(diagramIdentifier, genericTypeId)
      } else {
        visitGenericUsage(diagramIdentifier, genericTypeId)
      }
      super.visitClassDiagramIdentifierDeclarationHolder(declarationHolder)
    }

    private fun collectGenericDeclaration(identifier: MermaidClassDiagramIdentifier, genericTypeId: MermaidGenericTypeId?) {
      genericTypeId ?: return
      declaredGenerics.add(identifier.text)
    }

    private fun visitGenericUsage(diagramIdentifier: MermaidClassDiagramIdentifier, genericTypeId: MermaidGenericTypeId?) {
      genericTypeId ?: return
      if (diagramIdentifier.text in declaredGenerics) return

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

  class RemoveGenericToDeclarationQuickFix() : UndeclaredGenericLocalQuickFix() {
    override fun getFamilyName() = MermaidBundle.message("fix.remove.generic.to.declaration")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      addGenericElement(project, descriptor)
      deleteGenericElement(descriptor)
    }
  }

  class AddGenericToDeclarationQuickFix() : UndeclaredGenericLocalQuickFix() {
    override fun getFamilyName() = MermaidBundle.message("fix.add.generic.to.declaration")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      addGenericElement(project, descriptor)
    }
  }

  class RemoveGenericQuickFix() : UndeclaredGenericLocalQuickFix() {
    override fun getFamilyName() = MermaidBundle.message("fix.remove.generic")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      deleteGenericElement(descriptor)
    }
  }

  abstract class UndeclaredGenericLocalQuickFix() : LocalQuickFix {
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
