// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.actions;

import com.intellij.uiDesigner.designSurface.GuiEditor;

import java.awt.*;

public final class MoveSelectionToDownAction extends AbstractMoveSelectionAction{
  public MoveSelectionToDownAction(final GuiEditor editor, boolean extend, final boolean moveToLast) {
    super(editor, extend, moveToLast);
  }

  @Override
  protected int calcDistance(final Point source, final Point point) {
    if(source.y >= point.y){
      return Integer.MAX_VALUE;
    }
    else{
      final int scale = (point.x - source.x) >= 0 ? 3 : 4;
      return (point.y - source.y) + Math.abs(point.x - source.x) * scale;
    }
  }

  @Override
  protected int getRowMoveDelta() {
    return 1;
  }
}
