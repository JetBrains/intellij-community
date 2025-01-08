// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.imports.mlapi.features

import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.QualifiedName
import com.jetbrains.ml.features.api.feature.*
import com.jetbrains.python.codeInsight.imports.mlapi.ImportCandidateContext
import com.jetbrains.python.codeInsight.imports.mlapi.ImportCandidateFeatures
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyImportElement

object ImportsFeatures : ImportCandidateFeatures() {
  object Features {
    val EXISTING_IMPORT_FROM_PREFIX = FeatureDeclaration.int("existing_import_from_prefix") { "A maximal prefix for which there exists some import from" }.nullable()
    val EXISTING_IMPORT_PREFIX = FeatureDeclaration.int("existing_import_prefix") { "A maximal prefix for which there exists some import" }.nullable()
    val NEEDED_IMPORT_FROM_PREFIX = FeatureDeclaration.int("needed_import_from_prefix") { "COMPONENT_COUNT - EXISTING_IMPORT_FROM_PREFIX" }.nullable()
    val NEEDED_IMPORT_PREFIX = FeatureDeclaration.int("needed_import_prefix") { "COMPONENT_COUNT - EXISTING_IMPORT_PREFIX" }.nullable()
  }

  override val featureComputationPolicy = FeatureComputationPolicy(false, true)

  override val featureDeclarations = extractFeatureDeclarations(Features)

  override suspend fun computeFeatures(instance: ImportCandidateContext, filter: FeatureFilter): List<Feature> = buildList {
    val importCandidate = instance.candidate
    val project = readAction { importCandidate.importable?.project } ?: return@buildList
    val editor = readAction { FileEditorManager.getInstance(project).selectedEditor } ?: return@buildList
    val psiFile = readAction { PsiManager.getInstance(project).findFile(editor.file) } ?: return@buildList

    importCandidate.path?.let {
      readAction {
        val componentCount = it.componentCount
        val depthFrom = depthImportsFrom(psiFile, it)
        val depth = depthImports(psiFile, it)
        add(Features.EXISTING_IMPORT_FROM_PREFIX with componentCount - depthFrom)
        add(Features.EXISTING_IMPORT_PREFIX with componentCount - depth)
        add(Features.NEEDED_IMPORT_FROM_PREFIX with depthFrom)
        add(Features.NEEDED_IMPORT_PREFIX with depth)
      }
    } ?: run {
      add(Features.EXISTING_IMPORT_FROM_PREFIX with 0)
      add(Features.EXISTING_IMPORT_PREFIX with 0)
      add(Features.NEEDED_IMPORT_FROM_PREFIX with 0)
      add(Features.NEEDED_IMPORT_PREFIX with 0)
    }
  }
}

private fun hasImportsFrom(fromImports:  List<PyFromImportStatement>, qName: QualifiedName): Boolean {
  return fromImports.mapNotNull { it.getImportSourceQName() }
    .any { qName == it }
}

private fun hasImports(importTargets:  List<PyImportElement>, qName: QualifiedName): Boolean {
  return importTargets.map { it.importedQName }.any { qName == it }
}

fun depthImportsFrom(file: PsiFile, qName: QualifiedName): Int {
  if (file !is PyFile) return 0
  val fromImports = file.fromImports
  var qNameCycle = qName
  while (qNameCycle.componentCount > 0) {
    if (hasImportsFrom(fromImports, qNameCycle)) return qNameCycle.componentCount
    qNameCycle = qNameCycle.removeLastComponent()
  }
  return qNameCycle.componentCount
}

fun depthImports(file: PsiFile, qName: QualifiedName): Int {
  if (file !is PyFile) return 0
  val importTargets = file.importTargets
  var qNameCycle = qName
  while (qNameCycle.componentCount > 0) {
    if (hasImports(importTargets, qNameCycle)) return qNameCycle.componentCount
    qNameCycle = qNameCycle.removeLastComponent()
  }
  return qNameCycle.componentCount
}