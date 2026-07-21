// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.ui

import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.util.io.URLUtil

/**
 * Pure parsing helpers for the install dialog's search field. The dialog hands every keystroke to
 * these functions to decide whether the user typed a package name, a URL to an sdist/wheel, a
 * local path to an unpacked source tree, or a free-form CLI command — none of which require any
 * platform/Swing state, so they live outside the dialog class and are unit-tested directly.
 */

/** `true` when [text] looks like a package URL (`http(s)://...`, `git+...`, `file://...`). */
internal fun isPackageUrl(text: String): Boolean = URLUtil.containsScheme(text)

/** `true` when [text] looks like a filesystem path: absolute, `~/...`, or starts with `./` / `.`. */
internal fun isLocalPath(text: String): Boolean =
  OSAgnosticPathUtil.isAbsolute(OSAgnosticPathUtil.expandUserHome(text)) || text.startsWith(".")

/** Whitespace-split CLI invocation: first whitespace token is the tool name, the rest are args. */
internal data class ParsedCliCommand(val toolName: String, val args: List<String>)

/**
 * Splits a user-typed CLI command into tool + args using whitespace as the separator. Returns
 * `null` when [command] is blank.
 *
 * Mirrors a `bash -c`-style split for simple cases — does not understand quoting or shell
 * escapes; the install dialog never feeds quoted strings through here (CLI specs only need a
 * single tool name followed by plain arguments).
 */
internal fun parseCliCommand(command: String): ParsedCliCommand? {
  val parts = command.trim().split(' ', '\t').filter { it.isNotEmpty() }
  val tool = parts.firstOrNull() ?: return null
  return ParsedCliCommand(toolName = tool, args = parts.drop(1))
}
