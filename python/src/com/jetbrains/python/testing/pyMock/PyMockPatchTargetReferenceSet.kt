// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyMock

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiDirectory
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
import com.jetbrains.python.psi.types.PyModuleType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.intellij.util.ProcessingContext

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

    // Empty string: provide a single reference for top-level module completion
    if (content.isEmpty()) {
      return arrayOf(PyMockSegmentReference(element, valueRange, listOf(""), 0, createAllowed))
    }

    val segments = content.split(".")
    var currentOffset = valueRange.startOffset

    return buildList {
      for ((index, segment) in segments.withIndex()) {
        val range = TextRange(currentOffset, currentOffset + segment.length)
        add(PyMockSegmentReference(element, range, segments, index, createAllowed))
        currentOffset += segment.length + 1  // +1 for the '.' separator
      }
    }.toTypedArray()
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
    // For packages, navigate to __init__.py instead of the directory
    val navigable = if (resolved is PsiDirectory) {
      PyUtil.turnDirIntoInitPy(resolved) ?: PyUtil.turnDirIntoInit(resolved) ?: resolved
    } else resolved
    return arrayOf(PsiElementResolveResult(navigable))
  }

  override fun isSoft(): Boolean = createAllowed

  override fun getVariants(): Array<Any> {
    if (segmentIndex == 0) {
      return getTopLevelModuleVariants()
    }
    val context = fromFoothold(element).copyWithMembers()
    val parent = resolveSegmentAt(segments, segmentIndex - 1, context) ?: return emptyArray()
    return getMemberVariants(parent, element).toTypedArray()
  }

  /**
   * Provides completion for the first segment — lists top-level packages/modules
   * from source roots visible to the current file.
   */
  private fun getTopLevelModuleVariants(): Array<Any> {
    val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return emptyArray()
    val psiManager = element.manager
    return buildList {
      for (root in ModuleRootManager.getInstance(module).sourceRoots) {
        val dir = psiManager.findDirectory(root) ?: continue
        addAll(PyModuleType.getSubModuleVariants(dir, element, null))
      }
    }.toTypedArray()
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
  val fullName = QualifiedName.fromComponents(segments.take(upToIndex + 1))

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

private fun resolveMemberIn(element: PsiElement, name: String): PsiElement? =
  findMemberByName(element, name)

/**
 * Returns completion variants for members of [element].
 * Handles PyFile (module members), PyClass (methods/attributes), and PsiDirectory (sub-modules).
 */
private fun getMemberVariants(element: PsiElement, location: PsiElement): List<Any> {
  // For packages (directories), list sub-modules and __init__.py members
  if (element is PsiDirectory) {
    val result = mutableListOf<Any>()
    result.addAll(PyModuleType.getSubModuleVariants(element, location, null))
    val initFile = PyUtil.turnDirIntoInit(element) as? PyFile
    if (initFile != null) {
      val moduleType = PyModuleType(initFile)
      val context = TypeEvalContext.codeCompletion(initFile.project, initFile)
      result.addAll(moduleType.getCompletionVariantsAsLookupElements(location, ProcessingContext(), true, true, context))
    }
    return result
  }

  val target = PyUtil.turnDirIntoInit(element) ?: element
  return when (target) {
    is PyFile -> {
      val moduleType = PyModuleType(target)
      val context = TypeEvalContext.codeCompletion(target.project, target)
      moduleType.getCompletionVariantsAsLookupElements(location, ProcessingContext(), true, true, context)
    }
    is PyClass -> target.getMethods().toList()
    else -> emptyList()
  }
}
