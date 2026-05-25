// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.configuration

/**
 * How the IDE locates the binary for a Python tool.
 *
 * - [INTERPRETER] — resolve via the project's Python interpreter (e.g. `<sdk>/bin/<tool>`)
 * - [PATH] — resolve via the system `$PATH` (or a user-supplied custom path)
 * - [UVX] — run the tool ephemerally through `uvx <tool>`. `uvx` itself is found on `$PATH` (or via custom path)
 */
enum class ExecutableDiscoveryMode {
  INTERPRETER,
  PATH,
  UVX,
}
