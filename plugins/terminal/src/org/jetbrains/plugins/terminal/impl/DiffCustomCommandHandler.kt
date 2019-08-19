// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.impl

import com.intellij.openapi.project.Project
import com.intellij.terminal.TerminalCustomCommandHandler

class DiffCustomCommandHandler : TerminalCustomCommandHandler {
  override fun isAcceptable(command: String): Boolean {
    return "diff" == command
  }

  override fun execute(project: Project, command: String): Boolean {
    return false
  }
}