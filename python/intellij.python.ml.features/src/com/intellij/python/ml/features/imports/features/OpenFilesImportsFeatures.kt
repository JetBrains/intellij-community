// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ml.features.imports.features

import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiManager
import com.jetbrains.mlapi.feature.Feature
import com.jetbrains.mlapi.feature.FeatureContainer
import com.jetbrains.mlapi.feature.FeatureDeclaration
import com.jetbrains.mlapi.feature.FeatureSet
import com.jetbrains.python.psi.PyFile
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private const val FILES_TO_WATCH = 8

object OpenFilesImportsFeatures : ImportCandidateFeatures(Features) {
  object Features : FeatureContainer {
    val OPEN_FILES_EXISTING_IMPORT_FROM_PREFIX: FeatureDeclaration<Int?> = FeatureDeclaration.int("open_files_existing_import_from_prefix") { "A maximal prefix for which there exists some import from" }.nullable()
    val OPEN_FILES_EXISTING_IMPORT_PREFIX: FeatureDeclaration<Int?> = FeatureDeclaration.int("open_files_existing_import_prefix") { "A maximal prefix for which there exists some import" }.nullable()
    val OPEN_FILES_NEEDED_IMPORT_FROM_PREFIX: FeatureDeclaration<Int?> = FeatureDeclaration.int("open_files_needed_import_from_prefix") { "COMPONENT_COUNT - OPEN_FILES_EXISTING_IMPORT_FROM_PREFIX" }.nullable()
    val OPEN_FILES_NEEDED_IMPORT_PREFIX: FeatureDeclaration<Int?> = FeatureDeclaration.int("open_files_needed_import_prefix") { "COMPONENT_COUNT - OPEN_FILES_EXISTING_IMPORT_PREFIX" }.nullable()
  }

  override suspend fun computeNamespaceFeatures(instance: ImportCandidateContext, filter: FeatureSet): List<Feature> = coroutineScope  {
    val importCandidate = instance.candidate
    if (importCandidate.path == null) {
      return@coroutineScope emptyList()
    }
    else {
      val path = importCandidate.path ?: return@coroutineScope emptyList()
      val componentCount = path.componentCount
      val project = importCandidate.importable?.project ?: return@coroutineScope emptyList()
      val allEditors = readAction {
        FileEditorManager.getInstance(project).allEditors
      }
      val editors = allEditors.copyOfRange(0, minOf(allEditors.size, FILES_TO_WATCH))
      var minImportFrom = componentCount
      var minImport = componentCount
      val deferredEditors = editors.map { editor ->
        async { readAction { PsiManager.getInstance(project).findFile(editor.file) as? PyFile } }
      }

      val deferredDepthCalculations = deferredEditors.map { deferredPsiFile ->
        async {
          val psiFile = deferredPsiFile.await()
          psiFile?.let {
            val minImportFromDeferred = readAction { componentCount - depthImportsFrom(it, path) }
            val minImportDeferred = readAction { componentCount - depthImports(it, path) }
            Pair(minImportFromDeferred, minImportDeferred)
          }
        }
      }
      for (deferredPair in deferredDepthCalculations) {
        val pair = deferredPair.await() ?: continue
        val currentMinImportFrom = pair.first
        val currentMinImport = pair.second

        minImportFrom = minOf(minImportFrom, currentMinImportFrom)
        minImport = minOf(minImport, currentMinImport)
      }
      return@coroutineScope buildList<Feature> {
        add(Features.OPEN_FILES_EXISTING_IMPORT_FROM_PREFIX with minImportFrom)
        add(Features.OPEN_FILES_EXISTING_IMPORT_PREFIX with minImport)
        add(Features.OPEN_FILES_NEEDED_IMPORT_FROM_PREFIX with componentCount - minImportFrom)
        add(Features.OPEN_FILES_NEEDED_IMPORT_PREFIX with componentCount - minImport)
      }
    }
  }
}
