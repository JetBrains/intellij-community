// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.state

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import javax.swing.tree.TreeNode

data class PythonGlobalVariables(val values: XValueChildrenList?, val tree: XDebuggerTree?)

interface PyRuntime {
  /**
   * This function returns global python variables from a running python interpreter
   */
  fun getGlobalPythonVariables(virtualFile: VirtualFile, project: Project): List<TreeNode>?
}