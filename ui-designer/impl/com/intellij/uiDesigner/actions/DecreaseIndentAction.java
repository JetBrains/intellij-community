package com.intellij.uiDesigner.actions;

import com.intellij.uiDesigner.RadComponent;

/**
 * @author yole
 */
public class DecreaseIndentAction extends IncreaseIndentAction {
  @Override protected int adjustIndent(final int indent) {
    return indent > 0 ? indent-1 : 0;
  }

  @Override protected boolean canAdjustIndent(final RadComponent component) {
    return super.canAdjustIndent(component) && component.getConstraints().getIndent() > 0;
  }
}
