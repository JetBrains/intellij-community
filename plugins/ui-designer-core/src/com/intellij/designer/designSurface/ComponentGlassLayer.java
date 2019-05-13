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
package com.intellij.designer.designSurface;

import com.intellij.designer.designSurface.tools.ToolProvider;

import javax.swing.*;
import java.awt.event.*;

/**
 * @author Alexander Lobas
 */
public final class ComponentGlassLayer implements KeyListener, MouseListener, MouseMotionListener {
  private final JComponent myComponent;
  private final ToolProvider myToolProvider;
  private final EditableArea myArea;

  public ComponentGlassLayer(JComponent component, ToolProvider provider, EditableArea area) {
    myComponent = component;
    myToolProvider = provider;
    myArea = area;

    myComponent.addKeyListener(this);
    myComponent.addMouseListener(this);
    myComponent.addMouseMotionListener(this);
  }

  public void dispose() {
    myComponent.removeKeyListener(this);
    myComponent.removeMouseListener(this);
    myComponent.removeMouseMotionListener(this);
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Keyboard
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public void keyTyped(KeyEvent event) {
    myToolProvider.processKeyEvent(event, myArea);
  }

  @Override
  public void keyPressed(KeyEvent event) {
    myToolProvider.processKeyEvent(event, myArea);
  }

  @Override
  public void keyReleased(KeyEvent event) {
    myToolProvider.processKeyEvent(event, myArea);
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Mouse
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public void mouseClicked(MouseEvent event) {
    myToolProvider.processMouseEvent(event, myArea);
  }

  @Override
  public void mousePressed(MouseEvent event) {
    myToolProvider.processMouseEvent(event, myArea);
  }

  @Override
  public void mouseReleased(MouseEvent event) {
    myToolProvider.processMouseEvent(event, myArea);
  }

  @Override
  public void mouseEntered(MouseEvent event) {
    myToolProvider.processMouseEvent(event, myArea);
  }

  @Override
  public void mouseExited(MouseEvent event) {
    myToolProvider.processMouseEvent(event, myArea);
  }

  @Override
  public void mouseDragged(MouseEvent event) {
    myToolProvider.processMouseEvent(event, myArea);
  }

  @Override
  public void mouseMoved(MouseEvent event) {
    myToolProvider.processMouseEvent(event, myArea);
  }
}