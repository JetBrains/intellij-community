// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.backend.hyperlinks.filter

internal fun CharSequence.isCharAt(index: Int, char: Char): Boolean {
  return index in indices && this[index] == char
}

/**
 * Returns `true` if the Win32 API removes trailing characters during path normalization.
 *
 * See [Path normalization / Trim characters](https://learn.microsoft.com/en-us/dotnet/standard/io/file-path-formats#trim-characters):
 *
 * > If the path doesn't end in a separator, all trailing periods and spaces (U+0020) are removed.
 * > If the last segment is simply a single or double period, it falls under the relative components rule above.
 *
 * So `C:\dir\a.txt.`, `C:\dir\a.txt...`, and `C:\dir\a.txt ` open `C:\dir\a.txt`.
 * Java NIO finds a file at such a path, and [TerminalNioFileLookup] uses Java NIO.
 *
 * Examples:
 * - `willBeTrimmedByWin32("C:\\dir\\a.txt...")` returns `true`.
 * - `willBeTrimmedByWin32("C:\\dir\\..")` returns `false`.
 */
internal fun willBeTrimmedByWin32(path: String): Boolean {
  if (!path.endsWith('.') && !path.endsWith(' ')) return false
  val lastName = path.takeLastWhile { it != '\\' && it != '/' }
  return lastName != "." && lastName != ".."
}
