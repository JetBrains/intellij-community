package com.intellij.space.tools

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerEx

val Project.toolWindowManager: ToolWindowManager get() = ToolWindowManager.getInstance(this)
val Project.toolWindowManagerEx: ToolWindowManagerEx get() = ToolWindowManagerEx.getInstanceEx(this)
