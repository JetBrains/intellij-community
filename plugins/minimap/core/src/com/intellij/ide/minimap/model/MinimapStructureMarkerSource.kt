// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.model

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresReadLock

/**
 * Resolved source for a minimap structure marker.
 *
 * At least one of [psiElement] or [range] should normally be provided.
 */
data class MinimapStructureMarkerSource(
  val psiElement: PsiElement?,
  val range: TextRange?
)

/**
 * Returns whether the offsets of [element] belong to [document].
 *
 * A structure view can list an element of another file, for example a member of a base class.
 * The check does not load the document of that other file.
 */
@RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
internal fun isInDocument(element: PsiElement, document: Document): Boolean {
  val file = element.containingFile ?: return false
  return PsiDocumentManager.getInstance(file.project).getCachedDocument(file) === document
}
