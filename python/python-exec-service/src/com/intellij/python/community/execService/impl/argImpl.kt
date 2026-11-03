// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import com.intellij.python.community.execService.FileReporter
import com.intellij.python.community.execService.RelativePath
import com.jetbrains.python.venvReader.Directory
import java.nio.file.Path


internal sealed interface Arg {
  data class StringArg(internal val arg: String) : Arg

  /**
   * An argument with a local path that must be available on the remote machine.
   */
  sealed interface LocalArg : Arg

  data class FileArg(internal val file: Path, internal val fileReporter: FileReporter) : LocalArg

  /**
   * The full [root] directory. [filesToReport] are given to the process.
   * If [download] is true, [root] is copied back from the remote machine after the process exits.
   * If [upload] is false, the launcher maps [root] and copies none of its files.
   * Do not set [download] to true when [upload] is false. A download can then delete the local files that the launcher did not copy.
   */
  data class DirArg(
    internal val root: Directory,
    internal val filesToReport: List<Pair<DirEntry, FileReporter>>,
    internal val download: Boolean,
    internal val upload: Boolean,
  ) : LocalArg
}

/**
 * An entry of [Arg.DirArg.root] to give to the process.
 */
internal sealed interface DirEntry {
  /**
   * The directory itself.
   */
  data object Root : DirEntry

  /**
   * A file or a directory in the directory.
   */
  data class Child(val path: RelativePath) : DirEntry
}
