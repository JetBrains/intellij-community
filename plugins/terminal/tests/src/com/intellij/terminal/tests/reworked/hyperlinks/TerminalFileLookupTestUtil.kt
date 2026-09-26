// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.hyperlinks

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.path.EelPath
import org.jetbrains.plugins.terminal.hyperlinks.filter.TerminalFileKind
import org.jetbrains.plugins.terminal.hyperlinks.filter.TerminalFileLookup

/**
 * A descriptor of a fake environment. Paths of either OS family parse on any host.
 */
internal class TestEelDescriptor private constructor(override val osFamily: EelOsFamily) : EelDescriptor {
  override val name: String
    get() = "test-${osFamily.name.lowercase()}"

  companion object {
    val POSIX: TestEelDescriptor = TestEelDescriptor(EelOsFamily.Posix)
    val WINDOWS: TestEelDescriptor = TestEelDescriptor(EelOsFamily.Windows)
  }
}

/**
 * An in-memory file tree for the path finders that records every looked up path.
 */
internal class FakeTerminalFileLookup(val descriptor: EelDescriptor) : TerminalFileLookup {
  private val files = HashMap<EelPath, TerminalFileKind>()
  val lookedUpPaths: MutableList<EelPath> = mutableListOf()

  fun path(path: String): EelPath = EelPath.parse(path, descriptor)

  /** Adds the file at [path] with its parent directories and returns its path. */
  fun addFile(path: String, kind: TerminalFileKind = TerminalFileKind.FILE): EelPath {
    val eelPath = path(path)
    files[eelPath] = kind
    var parent = eelPath.parent
    while (parent != null && parent !in files) {
      files[parent] = TerminalFileKind.DIRECTORY
      parent = parent.parent
    }
    return eelPath
  }

  fun kindOf(path: EelPath): TerminalFileKind? = files[path]

  override fun lookup(path: EelPath): TerminalFileKind? {
    lookedUpPaths += path
    return files[path]
  }
}
