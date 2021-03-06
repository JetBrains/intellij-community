// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.tools

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerEx

val Project.toolWindowManager: ToolWindowManager get() = ToolWindowManager.getInstance(this)
val Project.toolWindowManagerEx: ToolWindowManagerEx get() = ToolWindowManagerEx.getInstanceEx(this)
