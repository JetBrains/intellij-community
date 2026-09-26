// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.intention

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.lexer.MermaidTokenTypeSets.WHITE_SPACES_WITHOUT_EOL
import com.intellij.mermaid.lang.lexer.MermaidTokens
import com.intellij.mermaid.lang.psi.MermaidComplexIdentifier
import com.intellij.mermaid.lang.psi.MermaidElementFactory
import com.intellij.mermaid.lang.psi.MermaidStateBody
import com.intellij.mermaid.lang.psi.MermaidVertex
import com.intellij.mermaid.lang.psi.hasType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.psi.util.nextLeaf
import com.intellij.psi.util.prevLeaf


class UnrenderableSpacesAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element.elementType in WHITE_SPACES_WITHOUT_EOL) {
      val parent = element.parent ?: return
      when (parent) {
        is MermaidComplexIdentifier -> {
          if (parent.parent is MermaidVertex) {
            annotateMermaidFlowchartVertex(holder)
          }
        }

        is MermaidStateBody -> {
          annotateStateIdentifier(element, holder)
        }
      }
    }
  }

  private fun annotateMermaidFlowchartVertex(holder: AnnotationHolder) {
    holder.newAnnotation(HighlightSeverity.ERROR, MermaidBundle.message("space.symbol.will.not.be.rendered"))
      .withFix(ChangeSpaceSymbolIntention())
      .withFix(RemoveSpaceIntention())
      .needsUpdateOnTyping()
      .create()
  }

  private fun annotateStateIdentifier(element: PsiElement, holder: AnnotationHolder) {
    if (element.prevLeaf().elementType == MermaidTokens.ID && element.nextLeaf().elementType == MermaidTokens.ID) {
      holder.newAnnotation(HighlightSeverity.WEAK_WARNING, MermaidBundle.message("space.symbol.will.not.be.rendered"))
        .withFix(ChangeSpaceSymbolIntention())
        .withFix(RemoveSpaceIntention())
        .needsUpdateOnTyping()
        .create()
    }
  }

  class ChangeSpaceSymbolIntention : BaseElementAtCaretIntentionAction() {
    override fun getFamilyName() = MermaidBundle.message("fix.not.rendered.space")

    override fun getText() = MermaidBundle.message("fix.change.space.symbol")

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
      val prevLeaf = element.prevLeaf() ?: return false
      val nextLeaf = element.nextLeaf() ?: return false
      return prevLeaf.hasType(MermaidTokens.ID) && nextLeaf.hasType(MermaidTokens.ID)
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
      val prevLeaf = element.prevLeaf() ?: return
      val nextLeaf = element.nextLeaf() ?: return
      val spaceElement = MermaidElementFactory.createSpaceElement(project, element.textLength)
      val newId = MermaidElementFactory.createIdElement(project, *arrayOf(prevLeaf, spaceElement, nextLeaf)) ?: return

      element.delete()
      prevLeaf.delete()
      nextLeaf.parent.addBefore(newId, nextLeaf)
      nextLeaf.delete()
    }
  }

  class RemoveSpaceIntention : BaseElementAtCaretIntentionAction() {
    override fun getFamilyName() = MermaidBundle.message("fix.not.rendered.space")

    override fun getText() = MermaidBundle.message("fix.remove.space")

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
      val prevLeaf = element.prevLeaf() ?: return false
      val nextLeaf = element.nextLeaf() ?: return false
      return prevLeaf.hasType(MermaidTokens.ID) && nextLeaf.hasType(MermaidTokens.ID)
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
      val prevLeaf = element.prevLeaf() ?: return
      val nextLeaf = element.nextLeaf() ?: return
      val newId = MermaidElementFactory.createIdElement(project, *arrayOf(prevLeaf, nextLeaf)) ?: return

      element.delete()
      prevLeaf.delete()
      nextLeaf.parent.addBefore(newId, nextLeaf)
      nextLeaf.delete()
    }
  }
}
