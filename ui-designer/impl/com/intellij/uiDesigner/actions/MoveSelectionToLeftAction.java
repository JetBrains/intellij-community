package com.intellij.uiDesigner.actions;

import com.intellij.uiDesigner.designSurface.GuiEditor;

import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class MoveSelectionToLeftAction extends AbstractMoveSelectionAction {
  public MoveSelectionToLeftAction(final GuiEditor editor, boolean extend, final boolean moveToLast) {
    super(editor, extend, moveToLast);
  }

  protected int calcDistance(final Point source, final Point point) {
    if(source.x <= point.x){
      return Integer.MAX_VALUE;
    }
    else{
      final int scale = (point.y - source.y) >= 0 ? 3 : 4;
      return (source.x - point.x) + Math.abs(point.y - source.y) * scale;
    }
  }

  protected int getColumnMoveDelta() {
    return -1;
  }
}
