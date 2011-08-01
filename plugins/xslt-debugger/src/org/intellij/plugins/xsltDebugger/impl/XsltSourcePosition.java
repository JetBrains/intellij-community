package org.intellij.plugins.xsltDebugger.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import org.intellij.plugins.xsltDebugger.rt.engine.Debugger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;

public class XsltSourcePosition implements XSourcePosition {
  private final Debugger.Locatable myLocation;
  private final XSourcePosition myPosition;

  XsltSourcePosition(Debugger.Locatable location, XSourcePosition position) {
    myLocation = location;
    myPosition = position;
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
    final XSourcePosition position = XDebuggerUtil.getInstance().createPosition(file, line);
    return line >= 0 && position != null ? new XsltSourcePosition(location, position) : null;
  }

  @Override
  public int getLine() {
    return myPosition.getLine();
  }

  @Override
  public int getOffset() {
    return myPosition.getOffset();
  }

  @NotNull
  @Override
  public VirtualFile getFile() {
    return myPosition.getFile();
  }

  @NotNull
  @Override
  public Navigatable createNavigatable(@NotNull Project project) {
    return myPosition.createNavigatable(project);
  }

  public Debugger.Locatable getLocation() {
    return myLocation;
  }
}
