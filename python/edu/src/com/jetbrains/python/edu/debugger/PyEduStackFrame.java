package com.jetbrains.python.edu.debugger;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XSourcePosition;
import com.jetbrains.python.debugger.PyFrameAccessor;
import com.jetbrains.python.debugger.PyStackFrame;
import com.jetbrains.python.debugger.PyStackFrameInfo;
import org.jetbrains.annotations.NotNull;

public class PyEduStackFrame extends PyStackFrame {
  public static final String MODULE = "<module>";
  public static final String GLOBAL_FRAME = "Global Frame";
  private final PyStackFrameInfo myFrameInfo;
  private final XSourcePosition myPosition;

  public PyEduStackFrame(@NotNull Project project,
                         @NotNull PyFrameAccessor debugProcess,
                         @NotNull PyStackFrameInfo frameInfo,
                         XSourcePosition position) {
    super(project, debugProcess, frameInfo, position);
    myFrameInfo = frameInfo;
    myPosition = position;
  }

  @Override
  public void customizePresentation(@NotNull ColoredTextContainer component) {
    component.setIcon(AllIcons.Debugger.StackFrame);
    if (myPosition == null) {
      component.append("<frame not available>", SimpleTextAttributes.GRAY_ATTRIBUTES);
      return;
    }

    final VirtualFile file = myPosition.getFile();

    String frameName = myFrameInfo.getName();
    component.setIcon(MODULE.equals(frameName) ? AllIcons.FileTypes.Text : AllIcons.Nodes.Field);
    SimpleTextAttributes regularAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
    if (MODULE.equals(frameName)) {
      component.append(GLOBAL_FRAME, regularAttributes);
      component.append(" (" + file.getName() + ")", getGrayAttributes(regularAttributes));
    }
    else {
      component
        .append(MODULE.equals(frameName) ? GLOBAL_FRAME : frameName, regularAttributes);
    }
  }
}
