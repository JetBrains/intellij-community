// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.remote.vfs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.pom.Navigatable;
import com.intellij.util.PathMapper;
import com.intellij.util.PathMappingSettings;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.XSourcePositionWrapper;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.jetbrains.python.debugger.PyDebugProcess;
import com.jetbrains.python.debugger.PyLocalPositionConverter;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.debugger.PySourcePosition;
import com.jetbrains.python.remote.PyRemotePathMapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyRemotePositionConverter extends PyLocalPositionConverter {
  private final @NotNull PyRemotePathMapper myPathMapper;

  private final PyRemoteDebugVirtualFS myVirtualFS;

  public PyRemotePositionConverter(PyDebugProcess debugProcess, @NotNull PathMappingSettings pathMappingSettings) {
    this(debugProcess, PyRemotePathMapper.fromSettings(pathMappingSettings, PyRemotePathMapper.PyPathMappingType.USER_DEFINED));
  }

  public PyRemotePositionConverter(PyDebugProcess debugProcess, @NotNull PyRemotePathMapper pathMapper) {
    myPathMapper = pathMapper;
    myVirtualFS = new PyRemoteDebugVirtualFS(debugProcess, pathMapper, this);
  }

  public @NotNull PathMapper getPathMapper() {
    return myPathMapper;
  }

  @Override
  protected @NotNull PySourcePosition convertToPython(@NotNull String filePath, int line) {
    String path = myPathMapper.convertToRemote(filePath);

    return new PyRemoteSourcePosition(path, line);
  }

  @Override
  public XSourcePosition convertFromPython(@NotNull PySourcePosition position, String frameName) {
    String path = position.getFile();
    int line = position.getLine();
    return convert(path, line);
  }

  @Override
  protected VirtualFileSystem getLocalFileSystem() {
    return myVirtualFS;
  }

  @Override
  public PySignature convertSignature(PySignature signature) {
    String localPath = getPathMapper().convertToLocal(signature.getFile());
    return new PySignature(localPath, signature.getFunctionName()).addAllArgs(signature);
  }

  private @Nullable XSourcePosition convert(String path, int line) {
    final VirtualFile file = getVirtualFile(path);
    XSourcePosition xPosition = createXSourcePosition(file, line);
    if (xPosition != null) {
      return new XRemoteSourcePosition(xPosition);
    }
    else {
      return null;
    }
  }

  public static class XRemoteSourcePosition extends XSourcePositionWrapper {
    private VirtualFile myFile;

    public XRemoteSourcePosition(@NotNull XSourcePosition xPosition) {
      super(xPosition);

      myFile = xPosition.getFile();
    }

    @Override
    public @NotNull VirtualFile getFile() {
      return myFile;
    }

    public void setFile(VirtualFile file) {
      myFile = file;
    }

    @Override
    public @NotNull Navigatable createNavigatable(@NotNull Project project) {
      return XDebuggerUtilImpl.createNavigatable(project, this);
    }
  }
}
