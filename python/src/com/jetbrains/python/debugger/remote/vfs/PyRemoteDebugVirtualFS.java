// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.remote.vfs;

import com.google.common.collect.Maps;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.debugger.PyDebugProcess;
import com.jetbrains.python.debugger.PyLocalPositionConverter;
import com.jetbrains.python.remote.PyRemotePathMapper;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;

public class PyRemoteDebugVirtualFS extends VirtualFileSystem {
  @NonNls private static final String PROTOCOL = "remoteDebugVfs";

  private final PyRemotePathMapper myPathMapper;
  private final PyDebugProcess myDebugProcess;
  private final PyRemotePositionConverter myRemotePositionConverter;

  private final Map<String, PyRemoteDebugVirtualFile> myFileCache = Maps.newHashMap();

  public PyRemoteDebugVirtualFS(PyDebugProcess debugProcess,
                                PyRemotePathMapper pathMapper,
                                PyRemotePositionConverter remotePositionConverter) {
    myDebugProcess = debugProcess;
    myRemotePositionConverter = remotePositionConverter;
    myPathMapper = pathMapper;
  }

  @NotNull
  @Override
  public String getProtocol() {
    return PROTOCOL;
  }

  @Override
  public VirtualFile findFileByPath(@NotNull @NonNls String path) {
    String localPath = myPathMapper.convertToLocal(path);
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(localPath);

    if (file == null) {
      file = PyLocalPositionConverter.findEggEntry(LocalFileSystem.getInstance(), localPath);
    }

    if (file == null) {
      if (!myFileCache.containsKey(path)) {
        PyRemoteDebugVirtualFile vFile = new PyRemoteDebugVirtualFile(this, path, myPathMapper);
        myFileCache.put(path, vFile);
      }
      file = myFileCache.get(path);
    }
    return file;
  }

  public PyRemotePositionConverter getRemotePositionConverter() {
    return myRemotePositionConverter;
  }

  public PyDebugProcess getDebugProcess() {
    return myDebugProcess;
  }

  @Override
  public void refresh(boolean asynchronous) {
  }

  @Override
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return null;
  }

  @Override
  public void addVirtualFileListener(@NotNull VirtualFileListener listener) {
  }

  @Override
  public void removeVirtualFileListener(@NotNull VirtualFileListener listener) {
  }

  @Override
  protected void deleteFile(Object requestor, @NotNull VirtualFile vFile) throws IOException {
  }

  @Override
  protected void moveFile(Object requestor, @NotNull VirtualFile vFile, @NotNull VirtualFile newParent) throws IOException {
  }

  @Override
  protected void renameFile(Object requestor, @NotNull VirtualFile vFile, @NotNull String newName) throws IOException {
  }

  @NotNull
  @Override
  protected VirtualFile createChildFile(Object requestor, @NotNull VirtualFile vDir, @NotNull String fileName) throws IOException {
    throw new IncorrectOperationException();
  }

  @NotNull
  @Override
  protected VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile vDir, @NotNull String dirName) throws IOException {
    throw new IncorrectOperationException();
  }

  @NotNull
  @Override
  protected VirtualFile copyFile(Object requestor,
                                 @NotNull VirtualFile virtualFile,
                                 @NotNull VirtualFile newParent,
                                 @NotNull String copyName) throws IOException {
    return virtualFile;
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }
}
