// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.openapi.module.Module
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.PyProjectToolFactory
import com.intellij.python.pyproject.TomlTableSafeGetError
import com.intellij.python.pyproject.getOrIssue
import com.intellij.python.pyproject.safeGetArr
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.requirements.createNameReq
import com.jetbrains.python.requirements.psi.NameReq
import com.jetbrains.python.sdk.uv.UvPyProjectIssue.SafeGetError
import org.apache.tuweni.toml.TomlTable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed class UvPyProjectIssue {
  data class SafeGetError(val error: TomlTableSafeGetError) : UvPyProjectIssue()
}

@ApiStatus.Internal
data class UvPyProjectTable(
  val uvDevDependencies: List<String>?,
)

@ApiStatus.Internal
data class UvPyProject(val project: UvPyProjectTable?, val issues: List<UvPyProjectIssue>) {
  fun matchOutdatedPackages(
    module: Module,
    pyProject: PyProjectToml,
    outdatedPackages: Map<String, PythonOutdatedPackage>,
  ): List<NameReq> =
    setOf(
      *(pyProject.project?.dependencies?.project?.toTypedArray() ?: arrayOf()),
      *(pyProject.project?.dependencies?.dev?.toTypedArray() ?: arrayOf()),
      *(project?.uvDevDependencies?.toTypedArray() ?: arrayOf()),
    ).mapNotNull { depString ->
      createNameReq(module.project, depString)
    }.filter { pyReq ->
      pyReq.displayName in outdatedPackages
    }

  companion object : PyProjectToolFactory<UvPyProject> {
    override val tables: List<String> = listOf("tool.uv")

    override fun createTool(tables: Map<String, TomlTable?>): UvPyProject {
      val issues = mutableListOf<UvPyProjectIssue>()
      val table = tables["tool.uv"]

      if (table == null) {
        return UvPyProject(null, issues)
      }

      val uvDevDependencies = table.safeGetArr<String>("dev-dependencies").getOrIssue(issues, { SafeGetError(it) })

      return UvPyProject(
        UvPyProjectTable(
          uvDevDependencies
        ),
        issues,
      )
    }
  }
}