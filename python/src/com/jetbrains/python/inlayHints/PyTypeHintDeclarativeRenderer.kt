// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inlayHints

import com.intellij.codeInsight.hints.declarative.InlayActionData
import com.intellij.codeInsight.hints.declarative.PresentationTreeBuilder
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionNavigationHandler
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionPayload
import com.intellij.codeInsight.hints.declarative.impl.PresentationTreeBuilderImpl.Companion.MAX_SEGMENT_TEXT_LENGTH
import com.intellij.psi.createSmartPointer
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.getEffectiveLanguageLevel
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyCollectionType
import com.jetbrains.python.psi.types.PyLiteralStringType
import com.jetbrains.python.psi.types.PyLiteralType
import com.jetbrains.python.psi.types.PyNamedTupleType
import com.jetbrains.python.psi.types.PyTupleType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.psi.types.isNoneType

/**
 * Renders [type] as a PEP 484-compliant type hint into a declarative inlay, making class names clickable so that
 * Ctrl/Cmd-clicking a name navigates to the corresponding class definition.
 *
 * For the type shapes handled explicitly, the produced text matches [PythonDocumentationProvider.getTypeHint]; for
 * everything else the rendering falls back to that method's plain (non-navigable) text, so the inlay text always stays
 * in sync with the canonical renderer.
 */
internal fun PresentationTreeBuilder.printPyTypeHint(type: PyType?, context: TypeEvalContext) {
  when {
    type == null || type.isNoneType -> plainText(PythonDocumentationProvider.getTypeHint(type, context))
    // Literal[...] renders its expression text rather than a class name.
    type is PyLiteralType || type is PyLiteralStringType -> fallbackText(type, context)
    // tuple[...] (homogeneous/empty forms) and NamedTuple have dedicated formatting.
    type is PyTupleType || type is PyNamedTupleType -> fallbackText(type, context)
    type is PyUnionType -> printUnion(type, context)
    type is PyCollectionType && !type.isDefinition -> printGenericType(type, context)
    type is PyClassType && !type.isDefinition -> printClassType(type, context)
    else -> fallbackText(type, context)
  }
}

private fun PresentationTreeBuilder.printUnion(union: PyUnionType, context: TypeEvalContext) {
  // Without PEP 604 `X | Y` syntax, unions are rendered as Union[...]/Optional[...]; defer those to the canonical renderer.
  if (!PyTypingTypeProvider.isBitwiseOrUnionAvailable(context)) {
    fallbackText(union, context)
    return
  }
  val members = union.members
  // Optional: `X | None`, with the non-None member always rendered first (as PythonDocumentationProvider does).
  if (members.size == 2 && members.any { it.isNoneType }) {
    val nonNone = members.firstOrNull { !it.isNoneType }
    if (nonNone != null) {
      printPyTypeHint(nonNone, context)
      plainText(" | ")
      printPyTypeHint(members.first { it.isNoneType }, context)
      return
    }
  }
  // Plain `A | B | C`. Unions with the unknown type (null) or 2+ literals are formatted specially downstream.
  if (members.none { it == null || it.isNoneType } && members.count { it is PyLiteralType } < 2) {
    members.forEachIndexed { index, member ->
      if (index > 0) plainText(" | ")
      printPyTypeHint(member, context)
    }
    return
  }
  fallbackText(union, context)
}

private fun PresentationTreeBuilder.printGenericType(type: PyCollectionType, context: TypeEvalContext) {
  val name = type.name
  val origin = context.origin
  val builtinGenericsAvailable = origin == null || getEffectiveLanguageLevel(origin).isAtLeast(LanguageLevel.PYTHON39)
  // On older language levels typing.List/Dict/... are substituted for list[]/dict[]/...; defer that to the canonical renderer.
  if (name == null || (!builtinGenericsAvailable && PyTypingTypeProvider.TYPING_COLLECTION_CLASSES.containsKey(name))) {
    fallbackText(type, context)
    return
  }
  clickableClassName(name, type.pyClass)
  plainText("[")
  type.typeArguments.forEachIndexed { index, element ->
    if (index > 0) plainText(", ")
    printPyTypeHint(element, context)
  }
  plainText("]")
}

private fun PresentationTreeBuilder.printClassType(type: PyClassType, context: TypeEvalContext) {
  val name = type.name
  if (name == null) {
    fallbackText(type, context)
    return
  }
  clickableClassName(name, type.pyClass)
}

private fun PresentationTreeBuilder.clickableClassName(name: String, pyClass: PyClass) {
  val actionData = InlayActionData(PsiPointerInlayActionPayload(pyClass.createSmartPointer()),
                                   PsiPointerInlayActionNavigationHandler.HANDLER_ID)
  // The platform forbids a single text segment longer than MAX_SEGMENT_TEXT_LENGTH characters.
  for (segment in name.chunked(MAX_SEGMENT_TEXT_LENGTH)) {
    text(segment, actionData)
  }
}

private fun PresentationTreeBuilder.fallbackText(type: PyType?, context: TypeEvalContext) {
  plainText(PythonDocumentationProvider.getTypeHint(type, context))
}

private fun PresentationTreeBuilder.plainText(text: String) {
  // The platform forbids a single text segment longer than MAX_SEGMENT_TEXT_LENGTH characters.
  for (segment in text.chunked(MAX_SEGMENT_TEXT_LENGTH)) {
    text(segment)
  }
}
