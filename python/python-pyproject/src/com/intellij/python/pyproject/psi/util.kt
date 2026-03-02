package com.intellij.python.pyproject.psi

import com.intellij.psi.PsiFile
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.jetbrains.python.packaging.PyVersionSpecifiers
import org.jetbrains.annotations.ApiStatus
import org.toml.lang.psi.TomlKeyValueOwner
import org.toml.lang.psi.TomlTable


@ApiStatus.Internal
fun PsiFile.isPyProjectToml(): Boolean = this.name == PY_PROJECT_TOML

/**
 * Extracts a [PyVersionSpecifiers] from a `pyproject.toml` PSI file.
 *
 * Checks PEP 621 `project -> requires-python` first, then Poetry `tool.poetry.dependencies -> python`.
 * Returns [PyVersionSpecifiers.ANY_SUPPORTED] if neither is found.
 */
@ApiStatus.Internal
fun PsiFile.resolvePythonVersionSpecifiers(): PyVersionSpecifiers {
  val spec = findTable("project")?.findValue("requires-python")
             ?: findTable("tool.poetry.dependencies")?.findValue("python")
             ?: return PyVersionSpecifiers.ANY_SUPPORTED
  return PyVersionSpecifiers(spec)
}

private fun PsiFile.findTable(headerKey: String): TomlKeyValueOwner? =
  children.filterIsInstance<TomlTable>().firstOrNull { it.header.key?.text == headerKey }

private fun TomlKeyValueOwner.findValue(key: String): String? =
  entries.firstOrNull { it.key.text == key }?.value?.text?.removeSurrounding("\"")?.removeSurrounding("'")
