// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest;

import com.google.common.collect.Lists;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.rest.editor.RestPreviewProvider;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.PythonHelper.REST_RUNNER;

public class RestPythonPreviewProvider extends RestPreviewProvider {

  @Nullable
  public String toHtml(String text, VirtualFile virtualFile, Project project) {
    Module module = ProjectFileIndex.SERVICE.getInstance(project).getModuleForFile(virtualFile);
    if (module == null) return null;

    final Sdk sdk = PythonSdkType.findPythonSdk(module);
    if (sdk == null) {
      return null;
    }
    final String sdkHomePath = sdk.getHomePath();
    if (sdkHomePath == null) {
      return null;
    }

    final GeneralCommandLine commandLine = REST_RUNNER.newCommandLine(sdkHomePath, Lists.newArrayList("rst2html"));
    final ProcessOutput output = PySdkUtil.getProcessOutput(commandLine, virtualFile.getParent().getPath(), null, 5000,
                                                            text.getBytes(CharsetToolkit.UTF8_CHARSET), true);
    if (output.isCancelled() || output.isTimeout()) return null;

    return output.getStdout();
  }
}
