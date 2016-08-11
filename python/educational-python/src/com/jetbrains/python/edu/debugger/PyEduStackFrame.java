package com.jetbrains.python.edu.debugger;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueGroup;
import com.jetbrains.python.debugger.PyFrameAccessor;
import com.jetbrains.python.debugger.PyStackFrame;
import com.jetbrains.python.debugger.PyStackFrameInfo;
import icons.PythonEducationalIcons;
import icons.PythonPsiApiIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public class PyEduStackFrame extends PyStackFrame {
  public static final String MODULE = "<module>";
  public static final String GLOBAL_FRAME = "Globals";
  public static final String DOUBLE_UNDERSCORE = "__";
  public static final String BUILTINS_VALUE_NAME = "__builtins__";

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
    if (myPosition == null) {
      component.append("<frame not available>", SimpleTextAttributes.GRAY_ATTRIBUTES);
      return;
    }
    final VirtualFile file = myPosition.getFile();
    String frameName = myFrameInfo.getName();
    component.setIcon(MODULE.equals(frameName) ? PythonPsiApiIcons.PythonFile : PythonEducationalIcons.Field);
    if (MODULE.equals(frameName)) {
      component.append(GLOBAL_FRAME, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      component.append(" (" + file.getName() + ")", getGrayAttributes(SimpleTextAttributes.REGULAR_ATTRIBUTES));
    }
    else {
      component.append(frameName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }

  @Override
  protected void addChildren(@NotNull final XCompositeNode node,
                             @Nullable final XValueChildrenList children) {
    if (children == null) {
      node.addChildren(XValueChildrenList.EMPTY, true);
      return;
    }
    final Map<String, XValue> specialValues = new HashMap<>();
    XValueChildrenList filteredChildren = new XValueChildrenList();
    for (int i = 0; i < children.size(); i++) {
      String name = children.getName(i);
      XValue value = children.getValue(i);
      if (name.startsWith(DOUBLE_UNDERSCORE) && name.endsWith(DOUBLE_UNDERSCORE)) {
        specialValues.put(name, value);
        continue;
      }
      filteredChildren.add(name, value);
    }
    node.addChildren(filteredChildren, specialValues.isEmpty());
    if (specialValues.isEmpty()) {
      return;
    }
    addSpecialVars(node, specialValues);
  }

  private static void addSpecialVars(@NotNull XCompositeNode node, Map<String, XValue> specialValues) {
      XValue builtins = specialValues.get(BUILTINS_VALUE_NAME);
      if (builtins != null) {
        specialValues.remove(BUILTINS_VALUE_NAME);
        node.addChildren(XValueChildrenList.singleton("Builtins", builtins), false);
      }
      node.addChildren(XValueChildrenList.bottomGroup(createSpecialVarsGroup(specialValues)), true);
  }

  @NotNull
  private static XValueGroup createSpecialVarsGroup(final Map<String, XValue> specialValues) {
    return new XValueGroup("Special Variables") {
      @Nullable
      @Override
      public Icon getIcon() {
        return PythonEducationalIcons.SpecialVar;
      }

      @Override
      public void computeChildren(@NotNull XCompositeNode node) {
        XValueChildrenList list = new XValueChildrenList();
        for (Map.Entry<String, XValue> entry : specialValues.entrySet()) {
          list.add(entry.getKey(), entry.getValue());
        }
        node.addChildren(list, true);
      }
    };
  }
}
