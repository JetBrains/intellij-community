// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.conda

import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.PsiElement
import com.intellij.python.community.impl.conda.PyCondaBundle
import com.intellij.python.community.impl.conda.environmentYml.CondaEnvironmentYmlSdkUtils.envFileNames
import com.intellij.python.community.impl.conda.environmentYml.format.CondaEnvironmentYmlParser
import com.jetbrains.python.inspections.dependencies.DependenciesInspectionProvider
import com.jetbrains.python.inspections.dependencies.DependenciesMap
import com.jetbrains.python.isCondaVirtualEnv
import com.jetbrains.python.packaging.PyRequirement
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence

internal class CondaDependenciesInspectionProvider : DependenciesInspectionProvider<YAMLFile>(YAMLFile::class.java) {
  override fun provideDependencies(file: YAMLFile, sdk: Sdk): DependenciesMap? {
    if (file.name !in envFileNames || !sdk.isCondaVirtualEnv) {
      return null
    }

    // The pip section is handled via injecting requirements.txt language into the pip mapping, so no need to handle it in this inspection.
    val sequence = findDependenciesSequence(file) ?: return emptyMap()
    val dependenciesMap = mutableMapOf<PyRequirement, PsiElement>()

    for (item in sequence.items) {
      val value = item.value as? YAMLScalar ?: continue
      val dependency = CondaEnvironmentYmlParser.parseCondaDep(value.textValue) ?: continue
      dependenciesMap[dependency] = value
    }

    return dependenciesMap
  }

  override val emptyFileInspectionMessage: @InspectionMessage String
    get() = PyCondaBundle.message("inspection.dependencies.conda.environment.file.empty")

  private fun findDependenciesSequence(file: YAMLFile): YAMLSequence? {
    val topLevel = file.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return null
    val deps = topLevel.getKeyValueByKey("dependencies") ?: return null
    return deps.value as? YAMLSequence
  }
}
