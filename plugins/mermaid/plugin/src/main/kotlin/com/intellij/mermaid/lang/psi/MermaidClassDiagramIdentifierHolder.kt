package com.intellij.mermaid.lang.psi

interface MermaidClassDiagramIdentifierHolder : MermaidPsiElement {
  val classDiagramIdentifier: MermaidClassDiagramIdentifier
  val generic: MermaidGeneric?
}

fun MermaidClassDiagramIdentifierHolder.isQuoted(): Boolean {
  return classDiagramIdentifier.quotedClassIdentifier != null
}

fun MermaidClassDiagramIdentifierHolder.identifier(): MermaidPsiElement {
  return classDiagramIdentifier.quotedClassIdentifier ?: classDiagramIdentifier
}

interface MermaidClassDiagramIdentifierDeclarationHolder : MermaidClassDiagramIdentifierHolder
