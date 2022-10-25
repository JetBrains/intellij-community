package com.intellij.mermaid.lang.intention

import com.intellij.codeInspection.*
import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.psi.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentOfTypes

class UndeclaredGenericUsageInspection : LocalInspectionTool() {

  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    if (file !is MermaidFile) return null

    val result = mutableListOf<ProblemDescriptor>()
    file.accept(object : MermaidVisitor() {
      private val declaredGenerics = mutableSetOf<String>()
      private val declarations = mutableSetOf<String>()

      override fun visitElement(element: PsiElement) {
        super.visitElement(element)
        element.acceptChildren(this)
      }

      override fun visitClassStatement(classStatement: MermaidClassStatement) {
        collectDeclaration(classStatement.classDiagramIdentifier, classStatement.generic?.genericTypeId)
        super.visitClassStatement(classStatement)
      }

      override fun visitRelationStatement(relationStatement: MermaidRelationStatement) {
        val leftId = relationStatement.leftId
        collectDeclaration(leftId.classDiagramIdentifier, leftId.generic?.genericTypeId)

        val rightId = relationStatement.rightId
        collectDeclaration(rightId.classDiagramIdentifier, rightId.generic?.genericTypeId)

        super.visitRelationStatement(relationStatement)
      }

      override fun visitMemberStatement(memberStatement: MermaidMemberStatement) {
        visitGenericUsage(memberStatement.classDiagramIdentifier, memberStatement.generic?.genericTypeId)
        super.visitMemberStatement(memberStatement)
      }

      override fun visitAnnotationStatement(annotationStatement: MermaidAnnotationStatement) {
        visitGenericUsage(annotationStatement.classDiagramIdentifier, annotationStatement.generic?.genericTypeId)
        super.visitAnnotationStatement(annotationStatement)
      }

      private fun collectDeclaration(diagramIdentifier: MermaidClassDiagramIdentifier, genericTypeId: MermaidGenericTypeId?) {
        val id = diagramIdentifier.text
        if (declarations.add(id)) {
          collectGenericDeclaration(diagramIdentifier, genericTypeId)
        } else {
          visitGenericUsage(diagramIdentifier, genericTypeId)
        }
      }

      private fun collectGenericDeclaration(identifier: MermaidClassDiagramIdentifier, genericTypeId: MermaidGenericTypeId?) {
        genericTypeId ?: return
        declaredGenerics.add(identifier.text)
      }

      private fun visitGenericUsage(diagramIdentifier: MermaidClassDiagramIdentifier, genericTypeId: MermaidGenericTypeId?) {
        genericTypeId ?: return
        if (declaredGenerics.contains(diagramIdentifier.text)) return

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
    })

    return result.toTypedArray()
  }

  override fun runForWholeFile(): Boolean {
    return true
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
      val parent = genericElement.parentOfTypes(
        MermaidAnnotationStatement::class,
        MermaidMemberStatement::class,
        MermaidClassStatement::class,
        MermaidLeftId::class,
        MermaidRightId::class
      ) ?: return
      val elementId = when (parent) {
        is MermaidAnnotationStatement -> parent.classDiagramIdentifier
        is MermaidMemberStatement -> parent.classDiagramIdentifier
        is MermaidClassStatement -> parent.classDiagramIdentifier
        is MermaidLeftId -> parent.classDiagramIdentifier
        is MermaidRightId -> parent.classDiagramIdentifier
        else -> null
      } ?: return

      var declaration: MermaidClassDiagramIdentifier? = null
      var newParent: PsiElement? = null
      genericTypeId.containingFile.accept(object : MermaidVisitor() {
        override fun visitElement(element: PsiElement) {
          super.visitElement(element)
          element.acceptChildren(this)
        }

        override fun visitClassStatement(classStatement: MermaidClassStatement) {
          if (declaration != null) return

          if (collectDeclaration(classStatement.classDiagramIdentifier)) return
          super.visitClassStatement(classStatement)
        }

        override fun visitRelationStatement(relationStatement: MermaidRelationStatement) {
          if (declaration != null) return

          val leftId = relationStatement.leftId
          if (collectDeclaration(leftId.classDiagramIdentifier)) return

          val rightId = relationStatement.rightId
          if (collectDeclaration(rightId.classDiagramIdentifier)) return

          super.visitRelationStatement(relationStatement)
        }

        private fun collectDeclaration(diagramIdentifier: MermaidClassDiagramIdentifier): Boolean {
          val id = diagramIdentifier.text
          if (id == elementId.text) {
            declaration = diagramIdentifier
            newParent = diagramIdentifier.parent
            return true
          }
          return false
        }
      })

      if (declaration == null || newParent == null) return

      val generic = MermaidElementFactory.createGenericElement(project, genericTypeId.text) ?: return
      newParent!!.addAfter(generic, declaration!!)
    }
  }
}
