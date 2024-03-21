// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.python.community.impl.huggingFace.annotation.HuggingFaceIdentifierPsiElement
import com.intellij.python.community.impl.huggingFace.api.HuggingFaceApi
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceDatasetsCache
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceLastCacheRefresh
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceModelsCache
import com.intellij.python.community.impl.huggingFace.service.PyHuggingFaceBundle
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyPlainStringElementImpl
import org.jetbrains.annotations.ApiStatus
import java.time.Duration


@ApiStatus.Internal
object HuggingFaceUtil {
  private val huggingFaceRelevantLibraries = setOf(
    "diffusers", "transformers", "allennlp", "spacy",
    "asteroid", "flair", "keras", "sentence-transformers",
    "stable-baselines3", "adapters", "huggingface_hub",
  )

  private fun isAnyHFLibraryImportedInFile(file: PyFile): Boolean {
    val isDirectlyImported = file.importTargets.any { importStmt ->
      huggingFaceRelevantLibraries.any { lib -> importStmt.importedQName.toString().contains(lib) }
    }

    val isFromImported = file.fromImports.any { fromImport ->
      huggingFaceRelevantLibraries.any { lib -> fromImport.importSourceQName?.toString()?.contains(lib) == true }
    }

    val isQualifiedImported: Boolean = file.importTargets.any { importStmt: PyImportElement? ->
      huggingFaceRelevantLibraries.any { lib: String -> importStmt?.importedQName?.components?.contains(lib) == true }
    }
    return isDirectlyImported || isFromImported || isQualifiedImported
  }

  fun isAnyHFLibraryImportedInProject(project: Project): Boolean {
    var isLibraryImported = false

    ProjectFileIndex.getInstance(project).iterateContent { virtualFile ->
      if (virtualFile.extension in listOf("py", "ipynb")) {
        val pythonFile = PsiManager.getInstance(project).findFile(virtualFile)
        if (pythonFile is PyFile) {
          isLibraryImported = isLibraryImported or isAnyHFLibraryImportedInFile(pythonFile)
        }
      }
      !isLibraryImported
    }

    return isLibraryImported
  }

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

  fun triggerCacheFillIfNeeded(project: Project) {
    val refreshState = project.getService(HuggingFaceLastCacheRefresh::class.java)
    val currentTime = System.currentTimeMillis()
    if (currentTime - refreshState.lastRefreshTime < Duration.ofDays(1).toMillis()) {
      return
    }

    HuggingFaceApi.fillCacheWithBasicApiData(HuggingFaceEntityKind.MODEL,
                                             HuggingFaceModelsCache,
                                             HuggingFaceConstants.MAX_MODELS_IN_CACHE)
    HuggingFaceApi.fillCacheWithBasicApiData(HuggingFaceEntityKind.DATASET,
                                             HuggingFaceDatasetsCache,
                                             HuggingFaceConstants.MAX_DATASETS_IN_CACHE)
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
