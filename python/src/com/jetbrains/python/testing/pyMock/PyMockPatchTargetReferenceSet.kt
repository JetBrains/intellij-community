// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyMock

import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReference
import com.intellij.psi.ResolveResult
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext
import com.jetbrains.python.psi.resolve.fromFoothold
import com.jetbrains.python.psi.resolve.resolveQualifiedName
import com.jetbrains.python.psi.resolve.resolveTopLevelMember

/**
 * Splits the dotted target string of a `@patch("module.Class.attr")` decorator into
 * individual segment references, enabling navigation and completion for each part.
 */
class PyMockPatchTargetReferenceSet(
  private val element: PyStringLiteralExpression,
  private val createAllowed: Boolean,
) {
  fun createReferences(): Array<PsiReference> {
    val valueRange = ElementManipulators.getValueTextRange(element)
    val content = element.stringValue
    if (content.isEmpty()) return emptyArray()

    val segments = content.split(".")
    val refs = mutableListOf<PsiReference>()
    var currentOffset = valueRange.startOffset

    for ((index, segment) in segments.withIndex()) {
      if (segment.isNotEmpty()) {
        val range = TextRange(currentOffset, currentOffset + segment.length)
        refs.add(PyMockSegmentReference(element, range, segments, index, createAllowed))
      }
      currentOffset += segment.length + 1  // +1 for the '.' separator
    }

    return refs.toTypedArray()
  }
}

private class PyMockSegmentReference(
  element: PyStringLiteralExpression,
  rangeInElement: TextRange,
  private val segments: List<String>,
  private val segmentIndex: Int,
  private val createAllowed: Boolean,
) : PsiPolyVariantReferenceBase<PyStringLiteralExpression>(element, rangeInElement) {

  override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
    val context = fromFoothold(element).copyWithMembers()
    val resolved = resolveSegmentAt(segments, segmentIndex, context) ?: return emptyArray()
    return arrayOf(PsiElementResolveResult(resolved))
  }

  override fun isSoft(): Boolean = createAllowed

  override fun getVariants(): Array<Any> {
    if (segmentIndex == 0) return emptyArray()
    val context = fromFoothold(element).copyWithMembers()
    val parent = resolveSegmentAt(segments, segmentIndex - 1, context) ?: return emptyArray()
    return getMemberVariants(parent).toTypedArray()
  }
}

/**
 * Resolves segments[0..upToIndex] to a PSI element, handling both module paths and
 * class/attribute chains.
 */
internal fun resolveSegmentAt(
  segments: List<String>,
  upToIndex: Int,
  context: PyQualifiedNameResolveContext,
): PsiElement? {
  val fullName = QualifiedName.fromComponents(segments.subList(0, upToIndex + 1))

  // First try to resolve as a module or package
  val moduleResult = resolveQualifiedName(fullName, context).firstOrNull()
  if (moduleResult != null) return moduleResult

  // Then try as a top-level member (class, function, attr) of the prefix module
  val memberResult = resolveTopLevelMember(fullName, context)
  if (memberResult != null) return memberResult

  // Finally, resolve the parent and look up this segment as a member of it
  if (upToIndex > 0) {
    val parent = resolveSegmentAt(segments, upToIndex - 1, context) ?: return null
    return resolveMemberIn(parent, segments[upToIndex])
  }

  return null
}

/**
 * Looks up [name] as an attribute/method of [element], supporting PyFile and PyClass.
 */
private fun resolveMemberIn(element: PsiElement, name: String): PsiElement? {
  val target = PyUtil.turnDirIntoInit(element) ?: element
  return when (target) {
    is PyFile -> target.findTopLevelAttribute(name)
                 ?: target.findTopLevelFunction(name)
                 ?: target.findTopLevelClass(name)
    is PyClass -> target.findMethodByName(name, false, null)
                  ?: target.findInstanceAttribute(name, false)
                  ?: target.findClassAttribute(name, false, null)
    else -> null
  }
}

private fun getMemberVariants(element: PsiElement): List<PsiElement> {
  val target = PyUtil.turnDirIntoInit(element) ?: element
  return when (target) {
    is PyFile -> target.topLevelClasses + target.topLevelFunctions + (target.topLevelAttributes ?: emptyList<PsiElement>())
    is PyClass -> target.getMethods().toList()
    else -> emptyList()
  }
}
