// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.remote.ProcessControlWithMappings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyRemoteTracebackFilter extends PythonTracebackFilter {
  private final ProcessControlWithMappings myHandler;

  public PyRemoteTracebackFilter(Project project, String workingDirectory, ProcessControlWithMappings remoteProcessHandler) {
    super(project, workingDirectory);

    myHandler = remoteProcessHandler;
  }

  @Override
  @Nullable
  protected VirtualFile findFileByName(@NotNull String fileName) {
    VirtualFile vFile = super.findFileByName(fileName);
    if (vFile != null) {
      return vFile;
    }
    String localFile = myHandler.getMappingSettings().convertToLocal(fileName);
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(localFile);
    if (file != null && file.exists()) {
      return file;
    }
    return null;
  }
}
