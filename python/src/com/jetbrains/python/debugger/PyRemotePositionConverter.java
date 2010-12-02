package com.jetbrains.python.debugger;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class PyRemotePositionConverter extends PyLocalPositionConverter {
  private final String myLocalRoot;
  private final String myRemoteRoot;

  public PyRemotePositionConverter(final String localRoot, final String remoteRoot) {
    myLocalRoot = FileUtil.toSystemIndependentName(localRoot);
    myRemoteRoot = FileUtil.toSystemIndependentName(remoteRoot);
  }

  @NotNull
  @Override
  public PySourcePosition convert(@NotNull XSourcePosition position) {
    String path = FileUtil.toSystemIndependentName(position.getFile().getPath());
    if (myLocalRoot.length() > 0) {
      path = path.replace(myLocalRoot, myRemoteRoot);
    }
    return new PyLocalSourcePosition(path, position.getLine() + 1);
  }

  @Override
  public XSourcePosition convert(@NotNull PySourcePosition position) {
    String path = FileUtil.toSystemIndependentName(position.getFile());
    if (myRemoteRoot.length() > 0) {
      path = path.replace(myRemoteRoot, myLocalRoot);
    }
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    return createXSourcePosition(file, position.getLine());
  }
}
