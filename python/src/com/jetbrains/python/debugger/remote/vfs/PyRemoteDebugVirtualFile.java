// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.remote.vfs;

import com.intellij.testFramework.LightVirtualFile;
import com.jetbrains.python.remote.PyRemotePathMapper;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class PyRemoteDebugVirtualFile extends LightVirtualFile {
  private final String myRemotePath;
  private final PyRemoteDebugVirtualFS myVirtualFileSystem;
  private final PyRemotePathMapper myPathMapper;

  PyRemoteDebugVirtualFile(PyRemoteDebugVirtualFS virtualFileSystem, String path, PyRemotePathMapper pathMapper) {
    super(new File(path).getName());
    myVirtualFileSystem = virtualFileSystem;
    myRemotePath = path;
    myPathMapper = pathMapper;

    setFileType(PyRemoteDebugFileType.INSTANCE);
    setWritable(false);
  }

  @NotNull
  @Override
  public String getPath() {
    return myPathMapper.convertToLocal(myRemotePath);
  }

  @Override
  @NotNull
  public PyRemoteDebugVirtualFS getFileSystem() {
    return myVirtualFileSystem;
  }

  public String getRemotePath() {
    return myRemotePath;
  }

  public PyRemotePathMapper getPathMapper() {
    return myPathMapper;
  }
}
