package circlet.tools

import com.intellij.openapi.project.*
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ex.*

val Project.toolWindowManager: ToolWindowManager get() = ToolWindowManager.getInstance(this)
val Project.toolWindowManagerEx: ToolWindowManagerEx get() = ToolWindowManagerEx.getInstanceEx(this)
