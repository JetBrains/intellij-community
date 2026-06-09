// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
