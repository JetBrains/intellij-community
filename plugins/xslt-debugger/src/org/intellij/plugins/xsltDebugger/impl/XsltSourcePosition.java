package org.intellij.plugins.xsltDebugger.impl;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.xdebugger.XSourcePosition;
import org.intellij.plugins.xsltDebugger.rt.engine.Debugger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;

public class XsltSourcePosition implements XSourcePosition {
  private final Debugger.Locatable myLocation;
  private final VirtualFile myFile;
  private final int myLine;

  XsltSourcePosition(Debugger.Locatable location, VirtualFile file, int line) {
    myLocation = location;
    myFile = file;
    myLine = line;
  }

  @Nullable
  public static XSourcePosition create(Debugger.Locatable location) {
    final VirtualFile file;
    try {
      file = VfsUtil.findFileByURL(new URI(location.getURI()).toURL());
    } catch (Exception e) {
      // TODO log
      return null;
    }

    final int line = location.getLineNumber() - 1;
    return line >= 0 ? new XsltSourcePosition(location, file, line) : null;
  }

  @Override
  public int getLine() {
    return myLine;
  }

  @Override
  public int getOffset() {
    return 0;
  }

  @NotNull
  @Override
  public VirtualFile getFile() {
    return myFile;
  }

  @NotNull
  @Override
  public Navigatable createNavigatable(@NotNull Project project) {
    return new OpenFileDescriptor(project, getFile(), getLine(), 0);
  }

  public Debugger.Locatable getLocation() {
    return myLocation;
  }
}
