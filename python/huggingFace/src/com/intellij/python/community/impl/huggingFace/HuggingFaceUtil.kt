// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.python.community.impl.huggingFace.annotation.HuggingFaceIdentifierPsiElement
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceDatasetsCache
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceModelsCache
import com.intellij.python.community.impl.huggingFace.service.PyHuggingFaceBundle
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.impl.PyPlainStringElementImpl
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
object HuggingFaceUtil {
  fun isWhatHuggingFaceEntity(text: String): HuggingFaceEntityKind? {
    return when {
      isHuggingFaceModel(text) -> HuggingFaceEntityKind.MODEL
      isHuggingFaceDataset(text) -> HuggingFaceEntityKind.DATASET
      else -> null
    }
  }

  private fun isHuggingFaceModel(text: String): Boolean = HuggingFaceModelsCache.isInCache(text)

  private fun isHuggingFaceDataset(text: String): Boolean = HuggingFaceDatasetsCache.isInCache(text)

  fun isHuggingFaceEntity(text: String): Boolean = isHuggingFaceModel(text) || isHuggingFaceDataset(text)

  @NlsSafe
  fun extractTextFromPyTargetExpression(pyTargetExpression: PyTargetExpression): String {
    val parent = pyTargetExpression.parent
    return if (parent is PyAssignmentStatement) {
      (parent.assignedValue as? PyStringLiteralExpression)?.stringValue ?: PyHuggingFaceBundle.message("python.hugging.face.default.value")
    }
    else {
      PyHuggingFaceBundle.message("python.hugging.face.default.value")
    }
  }

  private fun extractStringAndKindFromStringLiteralExpression(stringValue: String?): Pair<String, HuggingFaceEntityKind>? {
    val entityKind = if (stringValue != null) isWhatHuggingFaceEntity(stringValue) else null
    return stringValue?.takeIf { entityKind != null }?.let { Pair(it, entityKind!!) }
  }

  fun extractStringValueAndEntityKindFromElement(element: PsiElement): Pair<String, HuggingFaceEntityKind>? {
    return when (val parent = element.parent) {
      is PyAssignmentStatement -> when (element) {
        is PyTargetExpression -> {
          val stringValue = (parent.assignedValue as? PyStringLiteralExpression)?.stringValue
          extractStringAndKindFromStringLiteralExpression(stringValue)
        }
        else -> null
      }
      is PyStringLiteralExpression -> when (element) {
        is HuggingFaceIdentifierPsiElement -> Pair(element.entityName, element.entityKind)
        is PyPlainStringElementImpl -> {
          extractStringAndKindFromStringLiteralExpression(parent.stringValue)
        }
        else -> null
      }
      is PyArgumentList -> when (element) {
        is PyStringLiteralExpression -> {
          extractStringAndKindFromStringLiteralExpression(element.stringValue)
        }
        else -> null
      }
      else -> null
    }
  }

  fun humanReadableNumber(rawNumber: Int): String {
    if (rawNumber < 1000) return rawNumber.toString()
    val suffixes = arrayOf("K", "M", "B", "T")
    var count = rawNumber.toDouble()
    var i = 0
    while (i < suffixes.size && count >= 1000) {
      count /= 1000
      i++
    }
    return String.format("%.1f%s", count, suffixes[i - 1])
  }
}
