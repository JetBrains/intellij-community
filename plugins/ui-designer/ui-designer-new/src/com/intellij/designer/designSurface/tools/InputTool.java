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

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * @author Alexander Lobas
 */
public abstract class InputTool {
  /**
   * The final state for a tool to be in. Once a tool reaches this state, it will not change states
   * until it is activated() again.
   */
  protected static final int STATE_NONE = 0;
  /**
   * The first state that a tool is in. The tool will generally be in this state immediately
   * following {@link #activate()}.
   */
  protected static final int STATE_INIT = 1;
  /**
   * The state indicating that one or more buttons is pressed, but the user has not moved past the
   * drag threshold. Many tools will do nothing during this state but wait until
   * {@link #STATE_DRAG_IN_PROGRESS} is entered.
   */
  protected static final int STATE_DRAG = 2;
  /**
   * The state indicating that the drag detection theshold has been passed, and a drag is in
   * progress.
   */
  protected static final int STATE_DRAG_IN_PROGRESS = 3;
  /**
   * The state indicating that an input event has invalidated the interaction. For example, during a
   * mouse drag, pressing additional mouse button might invalidate the drag.
   */
  protected static final int STATE_INVALID = 4;

  public void keyTyped(KeyEvent e, EditableArea area) throws Exception {
  }

  public void keyPressed(KeyEvent e, EditableArea area) throws Exception {
  }

  public void keyReleased(KeyEvent e, EditableArea area) throws Exception {
  }

  public void mouseDown(MouseEvent e, EditableArea area) throws Exception {
  }

  public void mouseUp(MouseEvent e, EditableArea area) throws Exception {
  }

  public void mouseMove(MouseEvent e, EditableArea area) throws Exception {
  }

  public void mouseDrag(MouseEvent e, EditableArea area) throws Exception {
  }

  public void mouseDoubleClick(MouseEvent e, EditableArea area) throws Exception {
  }

  public void mouseEntered(MouseEvent e, EditableArea area) throws Exception {
  }

  public void mouseExited(MouseEvent e, EditableArea area) throws Exception {
  }
}