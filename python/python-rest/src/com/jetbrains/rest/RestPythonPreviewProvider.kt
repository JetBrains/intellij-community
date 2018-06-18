// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest

import com.google.common.collect.Lists
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.PythonHelper.REST_RUNNER
import com.jetbrains.python.sdk.PySdkUtil
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.rest.editor.RestPreviewProvider

class RestPythonPreviewProvider : RestPreviewProvider() {

  override fun toHtml(text: String, virtualFile: VirtualFile, project: Project): Pair<String, String>? {
    val module = ProjectFileIndex.SERVICE.getInstance(project).getModuleForFile(virtualFile) ?: return null

    val sdk = PythonSdkType.findPythonSdk(module) ?: return Pair("", "No Python interpreter configured for the project.")
    val commandLine = REST_RUNNER.newCommandLine(sdk, Lists.newArrayList("rst2html"))
    val output = PySdkUtil.getProcessOutput(commandLine, virtualFile.parent.path, null, 5000,
                                            text.toByteArray(CharsetToolkit.UTF8_CHARSET), true)
    return if (output.isCancelled || output.isTimeout)
      Pair("", "Failed to generate html.")
    else Pair(output.stdout, "<h3>Error output:</h3>" + output.stderr)
  }
}
