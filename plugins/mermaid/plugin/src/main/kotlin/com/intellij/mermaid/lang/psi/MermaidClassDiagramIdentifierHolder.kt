package com.intellij.mermaid.lang.psi

interface MermaidClassDiagramIdentifierHolder: MermaidPsiElement {
  val classDiagramIdentifier: MermaidClassDiagramIdentifier
  val generic: MermaidGeneric?
}

interface MermaidClassDiagramIdentifierDeclarationHolder: MermaidClassDiagramIdentifierHolder
