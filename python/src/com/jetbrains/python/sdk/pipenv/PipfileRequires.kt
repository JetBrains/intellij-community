// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.pipenv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.tuweni.toml.Toml
import org.apache.tuweni.toml.TomlInvalidTypeException
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * The Python version specifier the `Pipfile` in [projectDir] declares, or `null` when it declares none.
 *
 * A declared version is a real input rather than a record: `pipenv install` with no `--python` picks the interpreter
 * from it. Without one pipenv falls back to its own scan, which can land on a pre-release.
 *
 * `[requires]` holds either `python_version` for a minor version (`"3.9"` means any 3.9.x) or `python_full_version` for
 * an exact one. The docs present them as alternatives and state no rule for a file that sets both, so the minor-level
 * key wins: its meaning matches the choice a caller offers, which is one entry per minor version.
 *
 * Returns `null` for anything unreadable — a missing file, an I/O failure, malformed TOML, or a key holding the wrong
 * type. "Nothing is declared" is the safe answer, because the alternative reads as "no version is acceptable".
 */
internal suspend fun pipfileRequiresPython(projectDir: Path): String? = withContext(Dispatchers.IO) {
  val pipfile = projectDir.resolve(PIP_FILE)
  if (!pipfile.exists()) return@withContext null
  val content = try {
    pipfile.readText()
  }
  catch (_: IOException) {
    return@withContext null
  }
  val parsed = Toml.parse(content)
  if (parsed.hasErrors()) return@withContext null
  try {
    // Every tuweni getter throws when the key holds another type — the trap PY-91089 hit on `pyproject.toml`.
    val requires = parsed.getTable("requires") ?: return@withContext null
    requires.getString("python_version")?.let { pipfileVersionToSpecifier(it) }
    ?: requires.getString("python_full_version")?.let { pipfileVersionToSpecifier(it) }
  }
  catch (_: TomlInvalidTypeException) {
    null
  }
}

/** Operators [com.jetbrains.python.packaging.PyVersionSpecifiers] reads, so a value already carrying one is kept. */
private const val SPECIFIER_OPERATOR_CHARS: String = "=!<>~^"

/**
 * Turns a `Pipfile` `[requires]` value into a PEP 440 specifier: `"3.9"` becomes `"==3.9"` and `"3.9.23"` becomes
 * `"==3.9.23"`, while `">=3.9"` passes through. The same shape `HatchPythonSpec` produces for a hatch environment.
 *
 * A pinned patch needs no special handling. [com.jetbrains.python.packaging.PyVersionSpecifiers] is matched against a
 * `major.minor` version, whose absent patch component drops the patch comparison, so `==3.9.23` admits 3.9 and rejects
 * every other minor version — which is the intent.
 *
 * `null` when nothing usable is left, which keeps the caller's general rules instead of narrowing to nothing.
 */
internal fun pipfileVersionToSpecifier(declared: String): String? {
  val value = declared.trim().takeIf { it.isNotBlank() } ?: return null
  if (value[0] in SPECIFIER_OPERATOR_CHARS) return value
  // A bare version is not a specifier yet, so it becomes an equality one. Anything that is not a dotted number at all
  // declares nothing, rather than becoming a specifier that admits nothing.
  if (value.split('.').any { it.isEmpty() || !it.all(Char::isDigit) }) return null
  return "==$value"
}
