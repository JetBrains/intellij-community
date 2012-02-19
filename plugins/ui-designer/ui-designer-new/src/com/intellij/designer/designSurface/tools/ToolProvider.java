/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.designSurface.tools;

import com.intellij.designer.designSurface.EditableArea;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;

/**
 * @author Alexander Lobas
 */
public abstract class ToolProvider {
  private InputTool myTool;
  private EditableArea myArea;
  private MouseEvent myEvent;

  public InputTool getActiveTool() {
    return myTool;
  }

  public void setActiveTool(InputTool tool) {
    if (myTool != null) {
      myTool.deactivate();
    }

    myTool = tool;

    if (myTool != null) {
      myTool.setToolProvider(this);
      myTool.activate();

      // hack: update cursor
      if (myArea != null) {
        myTool.setArea(myArea);
        myTool.refreshCursor();
        try {
          myTool.mouseMove(myEvent, myArea);
        }
        catch (Exception e) {
          showError("Edit error: ", e);
        }
      }
    }
  }

  public abstract void loadDefaultTool();

  public void setArea(@Nullable EditableArea area) {
    myArea = area;
  }

  public void setEvent(MouseEvent event) {
    myEvent = event;
  }

  public abstract void showError(@NonNls String message, Throwable e);
}