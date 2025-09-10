// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.psi

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parents
import com.intellij.psi.util.startOffset
import com.intellij.util.containers.headTailOrNull
import com.intellij.util.containers.sequenceOfNotNull
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.yaml.meta.model.*

fun getKeysInBetween(value: YAMLValue, topSeq: YAMLSequence): List<String> =
  value.parents(false).takeWhile { it !== topSeq }.filterIsInstance<YAMLKeyValue>().map { it.keyText }.toList().reversed()

fun findStructuralSiblings(value: YAMLValue): Sequence<YAMLPsiElement> {
  val topSeq = value.parentOfType<YAMLSequence>() ?: return emptySequence()
  return findStructuralSiblings(topSeq, getKeysInBetween(value, topSeq))
}

fun findStructuralSiblings(topSeq: YAMLSequence, keys: List<String>): Sequence<YAMLPsiElement> {
  if (keys.isEmpty()) return topSeq.items.asSequence().mapNotNull { it.value }

  fun collect(root: YAMLKeyValue, keys: List<String>): Sequence<YAMLPsiElement> {
    val (head, tail) = keys.headTailOrNull() ?: return sequenceOf(root)
    if (root.keyText != head) return emptySequence()

    return when (val v = root.value) {
      is YAMLSequenceItem -> v.keysValues.asSequence().flatMap { collect(it, tail) }
      is YAMLMapping -> v.keyValues.asSequence().flatMap { collect(it, tail) }
      is YAMLKeyValue -> collect(v, tail)
      else -> if (tail.isEmpty()) sequenceOfNotNull(v) else emptySequence()
    }
  }

  return topSeq.items.asSequence().flatMap { it.keysValues }.flatMap { collect(it, keys) }
}

private val types = sequenceOf(YamlBooleanType.getSharedInstance(),
                               YamlNumberType.getInstance(false),
                               YamlStringType.getInstance(), YamlAnything.getInstance())

private fun isValid(meta: YamlMetaType, value: YAMLValue): Boolean {
  val tagText = value.tag?.text
  if (tagText != null && !meta.isSupportedTag(tagText)) return false

  val problemsHolder = ProblemsHolder(InspectionManager.getInstance(value.project), value.containingFile, false)
  meta.validateValue(value, problemsHolder)
  return !problemsHolder.hasResults()
}

@ApiStatus.Experimental
fun estimatedType(scalar: YAMLScalar): YamlMetaType? = types.firstOrNull { isValid(it, scalar) }

/**
 * Returns the closest ancestor of [element] that has no indentation (is at the start of line).
 * In short, this helps to find the containing top-level Key-Value in non-empty documents.
 */
internal fun Document.findClosestAncestorWithoutIndent(element: PsiElement): PsiElement {
  var current = element
  while (!isAtStartOfLine(current)) {
    // It is not possible to reach the root here, because it would mean the root itself is indented - where would the indent node be then?
    current = current.parent ?: error("the root of the PSI tree cannot be indented itself")
  }
  return current
}

private fun Document.isAtStartOfLine(element: PsiElement): Boolean =
  getLineStartOffset(getLineNumber(element.startOffset)) == element.startOffset
