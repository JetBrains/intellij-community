// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.pipenv

import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.python.community.impl.pipenv.PipEnvBundle
import com.jetbrains.python.inspections.dependencies.DependenciesInspectionProvider
import com.jetbrains.python.inspections.dependencies.DependenciesMap
import com.jetbrains.python.sdk.pipenv.PIP_FILE
import com.jetbrains.python.sdk.pipenv.PipEnvParser
import org.toml.lang.psi.TomlFile

internal class PipEnvDependenciesInspectionProvider : DependenciesInspectionProvider<TomlFile>(TomlFile::class.java) {
  override fun provideDependencies(file: TomlFile): DependenciesMap? {
    if (file.name != PIP_FILE) {
      return null
    }

    return PipEnvParser.getPipFileDependenciesMap(file)
  }

  override val emptyFileInspectionMessage: @InspectionMessage String
    get() =
      PipEnvBundle.message("inspection.dependencies.pipenv.pipfile.empty")
}
