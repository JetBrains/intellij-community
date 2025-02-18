// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.PsiFile
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.sdk.findAmongRoots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable
import kotlin.collections.get

const val PY_PROJECT_TOML: String = "pyproject.toml"

// TODO: extract into a shared package and make generic enough for other managers
class UvPyProject(file: PsiFile) {
  // TODO: should add categories, like deps, dev deps, optional deps etc.
  val requirements: List<UvRequirement>

  init {
    val depSources = mapOf(
      "project" to "dependencies",
      "dependency-groups" to "dev",
      "tool.uv" to "dev-dependencies"
    )

    requirements = file.children
      .mapNotNull { element ->
        (element as? TomlTable)?.let { table ->
          depSources[table.header.key?.text]?.let { header ->
            table.children
              .asSequence()
              .mapNotNull { it -> it as? TomlKeyValue }
              .find { it -> it.key.text == header }
              ?.value as? TomlArray
          }
        }
      }
      .flatMap { array ->
        array.elements.mapNotNull { value -> value as? TomlLiteral }
      }
      .mapNotNull { tomlLiteral ->
        PyRequirementParser.fromLine(StringUtil.unquoteString(tomlLiteral.text))?.let { requirement ->
          UvRequirement(requirement, tomlLiteral)
        }
      }
  }

  companion object {
    fun findFileBlocking(module: Module): VirtualFile? =
      findAmongRoots(module, PY_PROJECT_TOML)

    suspend fun findFile(module: Module): VirtualFile? =
      withContext(Dispatchers.IO) {
        findAmongRoots(module, PY_PROJECT_TOML)
      }

    fun fromModuleBlocking(module: Module): UvPyProject? =
      findFileBlocking(module)?.findPsiFile(module.project)?.let {
        UvPyProject(it)
      }
  }
}

data class UvRequirement(
  val pyRequirement: PyRequirement,
  val tomlLiteral: TomlLiteral,
)