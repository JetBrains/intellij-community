/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner.actions;

import com.intellij.uiDesigner.designSurface.GuiEditor;

import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class MoveSelectionToUpAction extends AbstractMoveSelectionAction{
  public MoveSelectionToUpAction(final GuiEditor editor, boolean extend, final boolean moveToLast) {
    super(editor, extend, moveToLast);
  }

  protected int calcDistance(final Point source, final Point point) {
    if(source.y <= point.y){
      return Integer.MAX_VALUE;
    }
    else{
      final int scale = (point.x - source.x) <= 0 ? 3 : 4;
      return (source.y - point.y) + Math.abs(point.x - source.x) * scale;
    }
  }

  protected int getRowMoveDelta() {
    return -1;
  }
}
