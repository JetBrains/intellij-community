// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.actions;

import com.intellij.uiDesigner.radComponents.RadComponent;


public class DecreaseIndentAction extends IncreaseIndentAction {
  @Override protected int adjustIndent(final int indent) {
    return indent > 0 ? indent-1 : 0;
  }

  @Override protected boolean canAdjustIndent(final RadComponent component) {
    return super.canAdjustIndent(component) && component.getConstraints().getIndent() > 0;
  }
}
