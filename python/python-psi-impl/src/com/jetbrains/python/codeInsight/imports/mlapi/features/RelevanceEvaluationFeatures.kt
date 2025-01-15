// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.imports.mlapi.features

import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.QualifiedName
import com.jetbrains.ml.api.feature.*
import com.jetbrains.python.codeInsight.imports.mlapi.ImportCandidateContext
import com.jetbrains.python.codeInsight.imports.mlapi.ImportCandidateFeatures
import com.jetbrains.python.sdk.PythonSdkUtil

enum class UnderscoresType {
  NO_UNDERSCORES,         // something (without underscores)
  SINGLE_LEADING,         // _something
  DOUBLE_LEADING,         // __something
  SINGLE_TRAILING,        // something_
  DOUBLE_TRAILING,        // something__
  SINGLE_LEADING_TRAILING, // _something_
  DOUBLE_LEADING_TRAILING, // __something__
  IRREGULAR // not from the above
}

enum class ModuleSourceType {
  STD_LIB,
  EXTERNAL_LIB,
  LOCAL_LIB
}


object RelevanceEvaluationFeatures : ImportCandidateFeatures() {
  object Features {
    val UNDERSCORES_IN_PATH: FeatureDeclaration<Int?> = FeatureDeclaration.int("underscores_in_path") { "number of prefix and suffix underscores in path" }.nullable()
    val MODULE_SOURCE_TYPE: FeatureDeclaration<ModuleSourceType?> = FeatureDeclaration.enum<ModuleSourceType>("module_source_type") { "info about lib being std, local, or external" }.nullable()
    val UNDERSCORES_TYPES_OF_PACKAGES: List<FeatureDeclaration<UnderscoresType?>> = (1..4).map { i -> FeatureDeclaration.enum<UnderscoresType>("underscores_types_of_package_$i") { "underscores types of package #$i" }.nullable() }
  }

  override val featureComputationPolicy: FeatureComputationPolicy = FeatureComputationPolicy(true, true)

  override val namespaceFeatureDeclarations: List<FeatureDeclaration<*>> = extractFeatureDeclarations(Features)

  override suspend fun computeNamespaceFeatures(instance: ImportCandidateContext, filter: FeatureFilter): List<Feature> = buildList {
    val importCandidate = instance.candidate
    add(Features.UNDERSCORES_IN_PATH with countBoundaryUnderscores(importCandidate.path))
    readAction {
      Features.UNDERSCORES_TYPES_OF_PACKAGES.withIndex().forEach { (i, featureDeclaration) ->
        add(featureDeclaration with getElementType(importCandidate.path, i))
      }
    }
    val baseElement = importCandidate.importable ?: return@buildList
    var vFile: VirtualFile? = null
    var sdk: Sdk? = null
    val containingFile = baseElement.containingFile
    if (containingFile != null) {
      vFile = containingFile.virtualFile
      sdk = readAction { PythonSdkUtil.findPythonSdk(containingFile) }
    }
    if (vFile != null) {
      add(Features.MODULE_SOURCE_TYPE with
            readAction {
              when {
                PythonSdkUtil.isStdLib(vFile, sdk) -> ModuleSourceType.STD_LIB
                ModuleUtilCore.findModuleForFile(vFile, baseElement.project) == null -> ModuleSourceType.EXTERNAL_LIB
                else -> ModuleSourceType.LOCAL_LIB
              }
            }
      )
    }
  }

  private fun countBoundaryUnderscores(qName: QualifiedName?): Int {
    if (qName == null) return 0
    return qName.components.sumOf { it.takeWhile { it == '_' }.length + it.takeLastWhile { it == '_' }.length }
  }

  private fun getUnderscoresType(s: String): UnderscoresType {
    val leadingUnderscores = s.takeWhile { it == '_' }.length
    val trailingUnderscores = s.reversed().takeWhile { it == '_' }.length

    return when {
      leadingUnderscores == 0 && trailingUnderscores == 0 -> UnderscoresType.NO_UNDERSCORES
      leadingUnderscores == 1 && trailingUnderscores == 0 -> UnderscoresType.SINGLE_LEADING
      leadingUnderscores == 2 && trailingUnderscores == 0 -> UnderscoresType.DOUBLE_LEADING
      leadingUnderscores == 0 && trailingUnderscores == 1 -> UnderscoresType.SINGLE_TRAILING
      leadingUnderscores == 0 && trailingUnderscores == 2 -> UnderscoresType.DOUBLE_TRAILING
      leadingUnderscores == 1 && trailingUnderscores == 1 -> UnderscoresType.SINGLE_LEADING_TRAILING
      leadingUnderscores == 2 && trailingUnderscores == 2 -> UnderscoresType.DOUBLE_LEADING_TRAILING
      else -> UnderscoresType.IRREGULAR
    }
  }

  private fun getElementType(qName: QualifiedName?, k: Int): UnderscoresType? {
    if (qName == null) return null
    val component = qName.components.getOrNull(k) ?: return null
    return getUnderscoresType(component)
  }
}
