package com.intellij.sh.backend.run

import com.intellij.sh.run.ShDefaultShellPathProvider

interface ShBackendShellPathProvider : ShDefaultShellPathProvider {
  override fun getDefaultShell(): String
}