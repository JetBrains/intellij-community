package com.intellij.uiDesigner.actions;

import com.intellij.uiDesigner.designSurface.GuiEditor;

import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class MoveSelectionToDownAction extends AbstractMoveSelectionAction{
  public MoveSelectionToDownAction(final GuiEditor editor, boolean extend, final boolean moveToLast) {
    super(editor, extend, moveToLast);
  }

  protected int calcDistance(final Point source, final Point point) {
    if(source.y >= point.y){
      return Integer.MAX_VALUE;
    }
    else{
      final int scale = (point.x - source.x) >= 0 ? 3 : 4;
      return (point.y - source.y) + Math.abs(point.x - source.x) * scale;
    }
  }

  protected int getRowMoveDelta() {
    return 1;
  }
}
