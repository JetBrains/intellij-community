package com.jetbrains.python.edu.debugger;


import com.intellij.icons.AllIcons;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class PyEduMagicDebugValue extends XNamedValue {

  private final Map<String, XValue> myValues;

  protected PyEduMagicDebugValue(@NotNull String name, Map<String, XValue> values) {
    super(name);
    myValues = values;
  }

  @Override
  public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
    node.setPresentation(AllIcons.Nodes.Artifact, new XValuePresentation() {

      @Override
      public void renderValue(@NotNull XValueTextRenderer renderer) {
        renderer.renderComment("{Python Pro Only}");
      }

      @NotNull
      @Override
      public String getSeparator() {
        return " ";
      }
    }, true);
  }

  @Override
  public void computeChildren(@NotNull XCompositeNode node) {
    XValueChildrenList children = new XValueChildrenList();
    for (Map.Entry<String, XValue> entry : myValues.entrySet()) {
      children.add(entry.getKey(), entry.getValue());
    }
    node.addChildren(children, true);
  }
}