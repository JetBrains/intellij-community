// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.render.DecimalRenderer;
import com.jetbrains.python.debugger.render.PyNodeRenderer;
import com.jetbrains.python.debugger.render.PyNodeRendererManager;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ViewAsGroup extends ActionGroup implements DumbAware {

  private static final AnAction[] myChildren;

  static {
    final List<AnAction> children = new ArrayList<>();
    for (PyNodeRenderer renderer : PyNodeRendererManager.getInstance().getAvailableRenderers()) {
      children.add(new RendererAction(renderer));
    }
    myChildren = children.toArray(EMPTY_ARRAY);
  }

  private static final class RendererAction extends ToggleAction {

    private final PyNodeRenderer myPyNodeRenderer;

    private RendererAction(PyNodeRenderer pyNodeRenderer) {
      super(pyNodeRenderer.getName());
      myPyNodeRenderer = pyNodeRenderer;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      final List<PyDebugValue> values = getSelectedValues(e);

      if (values.isEmpty()) return false;

      if (values.size() == 1) {
        PyNodeRenderer renderer = values.get(0).getDescriptor().getRenderer();
        return renderer == null ? myPyNodeRenderer instanceof DecimalRenderer : renderer == myPyNodeRenderer;
      }

      boolean allRenderersAreTheSame = values.stream().map(value -> value.getDescriptor().getRenderer()).distinct().count() == 1;

      if (allRenderersAreTheSame) {
        if (values.get(0).getDescriptor().getRenderer() == myPyNodeRenderer
            || (values.get(0).getDescriptor().getRenderer() == null && myPyNodeRenderer instanceof DecimalRenderer)) return true;
      }

      return false;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      if (!state) return;

      final List<XValueNodeImpl> selectedNodes = XDebuggerTreeActionBase.getSelectedNodes(e.getDataContext());

      for (XValueNodeImpl node : selectedNodes) {
        PyDebugValue value = (PyDebugValue) node.getValueContainer();
        value.getDescriptor().setRenderer(myPyNodeRenderer);
        if (value.getValue() == null) continue;
        String lbl = value.getDescriptor().getRenderer() != null ? value.getDescriptor().
          getRenderer().render(value.getValue()) : value.getValue();
        if (lbl != null) {
          node.setPresentation(node.getIcon(), value.getType(), lbl, node.getChildCount() != 0);
        }
      }
    }

    public PyNodeRenderer getRenderer() {
      return myPyNodeRenderer;
    }
  }

  public ViewAsGroup() {
    super(Presentation.NULL_STRING, true);
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return EMPTY_ARRAY;

    final List<PyDebugValue> values = getSelectedValues(e);

    if (values.isEmpty()) return EMPTY_ARRAY;

    for (PyDebugValue value : values) {
      for (AnAction child : myChildren) {
        if (!((RendererAction)child).getRenderer().isApplicable(value.getType())) return EMPTY_ARRAY;
      }
    }

    return myChildren;
  }

  @Override
  public boolean hideIfNoVisibleChildren() {
    return true;
  }

  @NotNull
  public static List<PyDebugValue> getSelectedValues(AnActionEvent e) {
    return StreamEx.of(XDebuggerTreeActionBase.getSelectedNodes(e.getDataContext()))
      .map(XValueNodeImpl::getValueContainer)
      .select(PyDebugValue.class)
      .toList();
  }
}
