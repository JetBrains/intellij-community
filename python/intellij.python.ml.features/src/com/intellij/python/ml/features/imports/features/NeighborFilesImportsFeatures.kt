// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ml.features.imports.features

import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.jetbrains.mlapi.feature.Feature
import com.jetbrains.mlapi.feature.FeatureContainer
import com.jetbrains.mlapi.feature.FeatureDeclaration
import com.jetbrains.mlapi.feature.FeatureSet
import com.jetbrains.python.psi.PyFile
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private const val FILES_TO_WATCH = 8

object NeighborFilesImportsFeatures : ImportCandidateFeatures(Features) {
  object Features : FeatureContainer {
    val NEIGHBOR_FILES_EXISTING_IMPORT_FROM_PREFIX: FeatureDeclaration<Int?> = FeatureDeclaration.int("neighbor_files_existing_import_from_prefix") { "A maximal prefix for which there exists some import from" }.nullable()
    val NEIGHBOR_FILES_EXISTING_IMPORT_PREFIX: FeatureDeclaration<Int?> = FeatureDeclaration.int("neighbor_files_existing_import_prefix") { "A maximal prefix for which there exists some import" }.nullable()
    val NEIGHBOR_FILES_NEEDED_IMPORT_FROM_PREFIX: FeatureDeclaration<Int?> = FeatureDeclaration.int("neighbor_files_needed_import_from_prefix") { "COMPONENT_COUNT - NEIGHBOR_FILES_EXISTING_IMPORT_FROM_PREFIX" }.nullable()
    val NEIGHBOR_FILES_NEEDED_IMPORT_PREFIX: FeatureDeclaration<Int?> = FeatureDeclaration.int("neighbor_files_needed_import_prefix") { "COMPONENT_COUNT - NEIGHBOR_FILES_EXISTING_IMPORT_PREFIX" }.nullable()
  }

  override suspend fun computeNamespaceFeatures(instance: ImportCandidateContext, filter: FeatureSet): List<Feature> = coroutineScope {
    val importCandidate = instance.candidate
    if (importCandidate.path == null) {
      return@coroutineScope emptyList()
    }

    val path = importCandidate.path ?: return@coroutineScope emptyList()
    val componentCount = path.componentCount
    val project = importCandidate.importable?.project ?: return@coroutineScope emptyList()
    val editor = readAction {
      FileEditorManager.getInstance(project).selectedEditor
    } ?: return@coroutineScope emptyList()

    var minImportFrom = componentCount
    var minImport = componentCount
    val allFiles = readAction { findAllVirtualFilesInSameDirectory(editor.file) }
    val files = allFiles.take(minOf(FILES_TO_WATCH, allFiles.size))

    val deferredEditors = files.map { file ->
      async { readAction { PsiManager.getInstance(project).findFile(file) as? PyFile } }
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
      add(Features.NEIGHBOR_FILES_EXISTING_IMPORT_FROM_PREFIX with minImportFrom)
      add(Features.NEIGHBOR_FILES_EXISTING_IMPORT_PREFIX with minImport)
      add(Features.NEIGHBOR_FILES_NEEDED_IMPORT_FROM_PREFIX with componentCount - minImportFrom)
      add(Features.NEIGHBOR_FILES_NEEDED_IMPORT_PREFIX with componentCount - minImport)
    }
  }

  private fun findAllVirtualFilesInSameDirectory(file: VirtualFile): List<VirtualFile> {
    val parentDirectory = file.parent
    if (parentDirectory != null && parentDirectory.isDirectory) {
      return parentDirectory.children.toList()
    }
    return emptyList()
  }
}
