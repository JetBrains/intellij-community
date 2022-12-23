package com.intellij.mermaid.lang.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement

interface MermaidDiagramDocument : PsiElement

interface MermaidDiagramInBracesDocument : PsiElement

interface MermaidFoldableElement : PsiElement

interface MermaidClassDiagramIdentifierHolder : PsiElement {
  val classDiagramIdentifier: MermaidClassDiagramIdentifier
  val generic: MermaidGeneric?
}

interface MermaidClassDiagramIdentifierDeclarationHolder : MermaidClassDiagramIdentifierHolder

interface MermaidGitGraphBranchIdentifierHolder : PsiElement {
  val gitGraphBranchIdentifier: MermaidGitGraphBranchIdentifier
}

fun MermaidGitGraphBranchIdentifierHolder.identifier() = gitGraphBranchIdentifier.identifier ?: gitGraphBranchIdentifier.quotedBranchIdentifier!!
fun MermaidGitGraphBranchIdentifierHolder.isQuoted() = gitGraphBranchIdentifier.quotedBranchIdentifier != null

interface MermaidPsiElement : NavigatablePsiElement

@Suppress("UnstableApiUsage")
open class MermaidNamedPsiElement(node: ASTNode) : ASTWrapperPsiElement(node), MermaidPsiElement,
  PsiExternalReferenceHost {
  override fun getName(): String {
    return text.trim()
  }
}
