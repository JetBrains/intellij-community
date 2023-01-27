package com.intellij.mermaid.lang.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.psi.NavigatablePsiElement

interface MermaidDiagramDocument: MermaidPsiElement

interface MermaidDiagramInBracesDocument: MermaidPsiElement

interface MermaidFoldableElement: MermaidPsiElement

interface MermaidClassDiagramIdentifierHolder: MermaidPsiElement {
  val classDiagramIdentifier: MermaidClassDiagramIdentifier
  val generic: MermaidGeneric?
}

interface MermaidClassDiagramIdentifierDeclarationHolder: MermaidClassDiagramIdentifierHolder

interface MermaidGitGraphBranchIdentifierHolder: MermaidPsiElement {
  val gitGraphBranchIdentifier: MermaidGitGraphBranchIdentifier
}

fun MermaidGitGraphBranchIdentifierHolder.identifier() = gitGraphBranchIdentifier.identifier ?: gitGraphBranchIdentifier.quotedBranchIdentifier!!
fun MermaidGitGraphBranchIdentifierHolder.isQuoted() = gitGraphBranchIdentifier.quotedBranchIdentifier != null

@Suppress("UnstableApiUsage")
abstract class MermaidNamedPsiElement(
  node: ASTNode
): ASTWrapperPsiElement(node), MermaidPsiElement, NavigatablePsiElement, PsiExternalReferenceHost {
  override fun getName(): String {
    return text.trim()
  }
}
