// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.openapi.module.Module
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.PyProjectToolFactory
import com.intellij.python.pyproject.getOrIssue
import com.intellij.python.pyproject.safeGetArr
import com.intellij.python.requirements.parser.PyRequirementParser.fromLine
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.sdk.uv.UvPyProjectIssue.SafeGetError
import org.apache.tuweni.toml.TomlTable

internal sealed class UvPyProjectIssue {
  data object SafeGetError : UvPyProjectIssue()
}

internal data class UvPyProjectTable(
  val uvDevDependencies: List<String>?,
)

internal data class UvPyProject(val project: UvPyProjectTable?, val issues: List<UvPyProjectIssue>) {
  fun matchOutdatedPackages(
    module: Module,
    pyProject: PyProjectToml,
    outdatedPackages: Map<String, PythonOutdatedPackage>,
  ): List<PyRequirement> =
    setOf(
      *pyProject.project.dependencies.project.toTypedArray(),
      *pyProject.allDepsFromGroups.toTypedArray(),
      *(project?.uvDevDependencies?.toTypedArray() ?: arrayOf()),
    ).mapNotNull { depString ->
      fromLine(depString, module.project)
    }.filter { pyReq ->
      pyReq.name in outdatedPackages
    }

  companion object : PyProjectToolFactory<UvPyProject> {
    override val tables: List<String> = listOf("tool.uv")

    override fun createTool(tables: Map<String, TomlTable?>): UvPyProject {
      val issues = mutableListOf<UvPyProjectIssue>()
      val table = tables["tool.uv"]

      if (table == null) {
        return UvPyProject(null, issues)
      }

      val uvDevDependencies = table.safeGetArr<String>("dev-dependencies").getOrIssue(issues, { SafeGetError })

      return UvPyProject(
        UvPyProjectTable(
          uvDevDependencies
        ),
        issues,
      )
    }
  }
}