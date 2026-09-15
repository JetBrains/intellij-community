// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.toolsets.general

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.model.psi.PsiSymbolService
import com.intellij.model.psi.impl.targetSymbols
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.DocumentUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock

/**
 * How the caller pointed at the symbol to rename.
 *
 * The fields are tried in the order [contextSnippet], [line] with [column], then [symbolName]
 * alone. [symbolName] is always a guard: a position that resolves to another name refuses the
 * rename instead of renaming the wrong symbol.
 *
 * [targetIndex] is no fourth way to point at the symbol. It picks from the candidate list that an
 * earlier call reported, so it runs on the branch the other fields select. See
 * [resolveRenameTarget].
 */
internal class RenameTargetRequest(
  val symbolName: String,
  val contextSnippet: String? = null,
  val line: Int? = null,
  val column: Int? = null,
  val targetIndex: Int? = null,
)

internal sealed interface RenameTargetResolution {
  class Resolved(val element: PsiElement) : RenameTargetResolution

  /** The name matches several declarations. The order is document order, and `targetIndex` is 1-based. */
  class Ambiguous(val candidates: List<PsiElement>) : RenameTargetResolution

  class Unresolved(val kind: RenameTargetProblem, val hint: String) : RenameTargetResolution
}

internal enum class RenameTargetProblem {
  SYMBOL_NOT_FOUND,
  SNIPPET_NOT_FOUND,
  SNIPPET_NOT_UNIQUE,
  POSITION_OUT_OF_BOUNDS,
  NAME_MISMATCH,
  TARGET_INDEX_OUT_OF_RANGE,
}

/**
 * The symbol the caller pointed at, or why the tool cannot tell which one it is.
 *
 * [RenameTargetRequest.targetIndex] picks from the candidate list of the branch the other fields
 * select. It must not run on a list of its own: a caller that retries after an ambiguous answer
 * repeats every field it sent, so a separate list would answer the pick with a symbol the caller
 * never saw.
 */
@RequiresReadLock
internal fun resolveRenameTarget(psiFile: PsiFile, document: Document, request: RenameTargetRequest): RenameTargetResolution {
  val resolution = resolveTarget(psiFile, document, request)
  val index = request.targetIndex ?: return resolution
  return when (resolution) {
    is RenameTargetResolution.Ambiguous -> pickCandidate(resolution.candidates, index)
    // The branch reads one symbol now, so only a pick of the first one agrees with it. The code
    // changed since the ambiguous answer, and any other index would rename an unreported symbol.
    is RenameTargetResolution.Resolved -> if (index == 1) resolution else pickCandidate(listOf(resolution.element), index)
    is RenameTargetResolution.Unresolved -> resolution
  }
}

private fun pickCandidate(candidates: List<PsiElement>, index: Int): RenameTargetResolution {
  if (index < 1 || index > candidates.size) {
    return RenameTargetResolution.Unresolved(
      RenameTargetProblem.TARGET_INDEX_OUT_OF_RANGE,
      "targetIndex $index is outside 1..${candidates.size}. This call reports ${candidates.size} candidate(s). " +
      "Send the same pathInProject, symbolName and contextSnippet as the call that listed them.",
    )
  }
  return RenameTargetResolution.Resolved(candidates[index - 1])
}

@RequiresReadLock
private fun resolveTarget(psiFile: PsiFile, document: Document, request: RenameTargetRequest): RenameTargetResolution {
  val snippet = request.contextSnippet
  if (snippet != null) return resolveBySnippet(psiFile, document, snippet, request.symbolName)

  if (request.line != null && request.column != null) {
    return resolveByPosition(psiFile, document, request.line, request.column, request.symbolName)
  }

  val declarations = namedDeclarations(psiFile, request.symbolName)
  return when (declarations.size) {
    0 -> RenameTargetResolution.Unresolved(
      RenameTargetProblem.SYMBOL_NOT_FOUND,
      "No symbol named '${request.symbolName}' is declared in this file. Pass the file that declares it.",
    )
    1 -> RenameTargetResolution.Resolved(declarations.first())
    else -> RenameTargetResolution.Ambiguous(declarations)
  }
}

/** Every declaration in [psiFile] that carries [symbolName], in document order. */
@RequiresReadLock
private fun namedDeclarations(psiFile: PsiFile, symbolName: String): List<PsiNamedElement> {
  val result = mutableListOf<PsiNamedElement>()
  psiFile.accept(object : PsiRecursiveElementWalkingVisitor() {
    override fun visitElement(element: PsiElement) {
      if (element is PsiNamedElement && element !is PsiFile && element.name == symbolName) result += element
      super.visitElement(element)
    }
  })
  return result
}

private fun resolveBySnippet(psiFile: PsiFile, document: Document, snippet: String, symbolName: String): RenameTargetResolution {
  if (snippet.isBlank()) {
    return RenameTargetResolution.Unresolved(RenameTargetProblem.SNIPPET_NOT_FOUND, "contextSnippet is blank.")
  }
  // An IntelliJ document always stores \n, while a caller can send the text with \r\n.
  val needle = snippet.replace("\r\n", "\n").replace('\r', '\n')
  if (!needle.contains(symbolName)) {
    return RenameTargetResolution.Unresolved(
      RenameTargetProblem.NAME_MISMATCH,
      "contextSnippet does not contain '$symbolName'. Pass a snippet that shows the symbol.",
    )
  }

  val text = document.text
  val start = text.indexOf(needle)
  if (start < 0) {
    return RenameTargetResolution.Unresolved(
      RenameTargetProblem.SNIPPET_NOT_FOUND,
      "contextSnippet is not in this file. Read the file again and copy the snippet from its current text.",
    )
  }
  if (text.indexOf(needle, start + 1) >= 0) {
    return RenameTargetResolution.Unresolved(
      RenameTargetProblem.SNIPPET_NOT_UNIQUE,
      "contextSnippet matches this file more than once. Extend it until it is unique.",
    )
  }

  val resolved = LinkedHashSet<PsiElement>()
  var at = needle.indexOf(symbolName)
  while (at >= 0) {
    elementsAt(psiFile, document, start + at).filterTo(resolved) { (it as? PsiNamedElement)?.name == symbolName }
    at = needle.indexOf(symbolName, at + 1)
  }
  return when (resolved.size) {
    0 -> RenameTargetResolution.Unresolved(
      RenameTargetProblem.SYMBOL_NOT_FOUND,
      "'$symbolName' inside contextSnippet does not resolve to a symbol that can be renamed.",
    )
    1 -> RenameTargetResolution.Resolved(resolved.first())
    else -> RenameTargetResolution.Ambiguous(resolved.toList())
  }
}

private fun resolveByPosition(psiFile: PsiFile, document: Document, line: Int, column: Int, symbolName: String): RenameTargetResolution {
  if (line < 1 || column < 1 || !DocumentUtil.isValidLine(line - 1, document)) {
    return RenameTargetResolution.Unresolved(
      RenameTargetProblem.POSITION_OUT_OF_BOUNDS,
      "Position $line:$column is outside this file. Both numbers are 1-based.",
    )
  }
  val offset = document.getLineStartOffset(line - 1) + column - 1
  // The offset has to stay on the line the caller named. A column past the end of the line would
  // otherwise land further down the file, and rename a symbol of the same name on another line.
  if (offset > document.getLineEndOffset(line - 1)) {
    return RenameTargetResolution.Unresolved(
      RenameTargetProblem.POSITION_OUT_OF_BOUNDS,
      "Column $column is past the end of line $line. Both numbers are 1-based.",
    )
  }

  val candidates = elementsAt(psiFile, document, offset)
  val matching = candidates.firstOrNull { (it as? PsiNamedElement)?.name == symbolName }
  if (matching != null) return RenameTargetResolution.Resolved(matching)

  val other = candidates.firstNotNullOfOrNull { (it as? PsiNamedElement)?.name }
  if (other != null) {
    return RenameTargetResolution.Unresolved(
      RenameTargetProblem.NAME_MISMATCH,
      "Position $line:$column resolves to '$other', not to '$symbolName'. Nothing was renamed.",
    )
  }
  return RenameTargetResolution.Unresolved(
    RenameTargetProblem.SYMBOL_NOT_FOUND,
    "No symbol that can be renamed sits at $line:$column.",
  )
}

/** The declarations that the symbol at [offset] points at, whether [offset] sits on a declaration or on a reference. */
@RequiresReadLock
private fun elementsAt(psiFile: PsiFile, document: Document, offset: Int): List<PsiElement> {
  val adjusted = TargetElementUtil.adjustOffset(psiFile, document, offset)
  val symbolService = PsiSymbolService.getInstance()
  val fromSymbols = targetSymbols(psiFile, adjusted).mapNotNull { symbolService.extractElementFromSymbol(it) }
  if (fromSymbols.isNotEmpty()) return fromSymbols

  val referenced = psiFile.findReferenceAt(adjusted)?.resolve()
  if (referenced != null) return listOf(referenced)

  // The last resort walks up to the declaration that holds the offset. That declaration is the
  // target only when the offset sits on its name. Otherwise the offset sits on a comment, a keyword
  // or whitespace inside it, and the caller pointed at no symbol at all.
  val declared = PsiTreeUtil.findElementOfClassAtOffset(psiFile, adjusted, PsiNamedElement::class.java, false)
  return listOfNotNull(declared?.takeIf { offsetIsOnTheName(it, adjusted) })
}

/**
 * Whether [offset] sits on the name of [declaration].
 *
 * A declaration that carries no name identifier answers false, so the caller reports that it found
 * no symbol. That is the safe answer: a refusal the caller corrects with a contextSnippet costs
 * less than a rename of a symbol the caller never pointed at.
 */
@RequiresReadLock
private fun offsetIsOnTheName(declaration: PsiNamedElement, offset: Int): Boolean {
  val nameIdentifier = (declaration as? PsiNameIdentifierOwner)?.nameIdentifier ?: return false
  return nameIdentifier.textRange?.containsOffset(offset) == true
}
