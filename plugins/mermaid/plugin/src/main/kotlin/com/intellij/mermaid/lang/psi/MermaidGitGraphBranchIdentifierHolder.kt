package com.intellij.mermaid.lang.psi

interface MermaidGitGraphBranchIdentifierHolder: MermaidPsiElement {
  val gitGraphBranchIdentifier: MermaidGitGraphBranchIdentifier
}

fun MermaidGitGraphBranchIdentifierHolder.isQuoted(): Boolean {
  return gitGraphBranchIdentifier.quotedBranchIdentifier != null
}

// TODO: Probably should return [MermaidIdentifier]
// TODO: [MermaidQuotedBranchIdentifier] should probably implement [MermaidIdentifier]
fun MermaidGitGraphBranchIdentifierHolder.branchIdentifier(): MermaidPsiElement {
  return gitGraphBranchIdentifier.identifier ?: gitGraphBranchIdentifier.quotedBranchIdentifier!!
}
