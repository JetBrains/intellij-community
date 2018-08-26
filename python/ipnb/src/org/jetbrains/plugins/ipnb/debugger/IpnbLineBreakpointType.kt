// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb.debugger

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.debugger.PyDebuggerEditorsProvider
import com.jetbrains.python.debugger.PyLineBreakpointType

class IpnbLineBreakpointType : PyLineBreakpointType(ID, "Ipnb Line Breakpoint", PyDebuggerEditorsProvider()) {

  override fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean {
    val stoppable = Ref.create(false)
    val document = FileDocumentManager.getInstance().getDocument(file)
    if (document != null) {
      lineHasStoppablePsi(project, file, line, PythonFileType.INSTANCE, document, UNSTOPPABLE_ELEMENTS, UNSTOPPABLE_ELEMENT_TYPES, stoppable
      )
    }

    return stoppable.get()
  }

  override fun getBreakpointsDialogHelpTopic(): String? {
    return "reference.dialogs.breakpoints"
  }

  companion object {
    val ID = "ipnb-line"
  }

}
