// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.features

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.eel.fs.EelFiles
import java.io.IOException
import java.nio.file.Path

private const val BLOCK_START: String = "# /// script"
private const val BLOCK_END: String = "# ///"

/**
 * Whether [text] carries a PEP 723 `script` metadata block, the thing that makes `uv run --script` install inline
 * dependencies. Only the presence of the block is decided here; its TOML body is uv's business.
 *
 * The block opens with a `# /// script` line, continues over lines that are either exactly `#` or start with `# `,
 * and closes with a `# ///` line.
 */
internal fun hasInlineScriptMetadata(text: CharSequence): Boolean {
  var inBlock = false
  for (rawLine in text.lineSequence()) {
    // Tolerate CRLF: the sequence keeps the trailing carriage return of a `\r\n` document.
    val line = rawLine.removeSuffix("\r")
    when {
      !inBlock -> if (line == BLOCK_START) inBlock = true
      line == BLOCK_END -> return true
      // Anything that is not a continuation comment ends the candidate block without closing it.
      line != "#" && !line.startsWith("# ") -> inBlock = false
    }
  }
  return false
}

/**
 * Whether the script at [path] carries a PEP 723 `script` metadata block.
 *
 * Prefers the loaded document, so that a block just typed into the editor counts before it is saved, and falls back to
 * reading the file directly — a run configuration may well point at a script the IDE has never opened. An unreadable
 * file is reported as carrying no metadata: detection only feeds a default that the user can override.
 */
internal fun hasInlineScriptMetadata(path: Path): Boolean {
  val text = try {
    val file = LocalFileSystem.getInstance().findFileByNioFile(path)
    when {
      file == null -> EelFiles.readString(path)
      else -> FileDocumentManager.getInstance().getCachedDocument(file)?.text ?: VfsUtilCore.loadText(file)
    }
  }
  catch (e: IOException) {
    fileLogger().warn("Cannot read $path to look for PEP 723 metadata", e)
    return false
  }
  return hasInlineScriptMetadata(text)
}
