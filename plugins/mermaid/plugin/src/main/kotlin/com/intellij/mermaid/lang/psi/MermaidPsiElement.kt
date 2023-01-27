package com.intellij.mermaid.lang.psi

import com.intellij.psi.PsiElement

// TODO: Make all elements implement [MermaidPsiElement].
//       Currently it won't be implemented for most of leaf elements.
/**
 * Marker interface for all Mermaid elements.
 */
interface MermaidPsiElement: PsiElement
