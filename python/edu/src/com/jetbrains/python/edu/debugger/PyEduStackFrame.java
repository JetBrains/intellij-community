package com.jetbrains.python.edu.debugger;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.python.debugger.PyFrameAccessor;
import com.jetbrains.python.debugger.PyStackFrame;
import com.jetbrains.python.debugger.PyStackFrameInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class PyEduStackFrame extends PyStackFrame {
  public static final String MODULE = "<module>";
  public static final String GLOBAL_FRAME = "Globals";
  public static final String DOUBLE_UNDERSCORE = "__";
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

  @Override
  protected void addChildren(@NotNull final XCompositeNode node,
                             @Nullable final XValueChildrenList children) {
    Map<String, XValue> specialValues = new HashMap<String, XValue>();
    XValueChildrenList newChildren = new XValueChildrenList();
    if (children == null) {
      node.addChildren(XValueChildrenList.EMPTY, true);
      return;
    }
    for (int i = 0; i < children.size(); i++) {
      String name = children.getName(i);
      XValue value = children.getValue(i);
      if (name.startsWith(DOUBLE_UNDERSCORE) && name.endsWith(DOUBLE_UNDERSCORE)) {
        specialValues.put(name, value);
      }
      else {
        newChildren.add(name, value);
      }
    }
    if (!specialValues.isEmpty()) {
      newChildren.add(new PyEduMagicDebugValue("special variables", specialValues));
    }
    node.addChildren(newChildren, true);
  }
}
