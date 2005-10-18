package com.intellij.uiDesigner.actions;

import com.intellij.uiDesigner.GuiEditor;

import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class MoveSelectionToRightAction extends AbstractMoveSelectionAction{
  public MoveSelectionToRightAction(final GuiEditor editor) {
    super(editor);
  }

  protected int calcDistance(final Point source, final Point point) {
    if(source.x >= point.x){
      return Integer.MAX_VALUE;
    }
    else{
      final int scale = (point.y - source.y) <= 0 ? 3 : 4;
      return (point.x - source.x) + Math.abs(point.y - source.y) * scale;
    }
  }
}
