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
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.feedbacks.AlphaFeedback;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadComponentVisitor;
import com.intellij.designer.utils.Cursors;
import com.intellij.openapi.application.ApplicationManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class MarqueeTracker extends InputTool {
  private static final Color myColor = new Color(47, 67, 96);

  private static final int TOGGLE_MODE = 1;
  private static final int APPEND_MODE = 2;

  private JComponent myFeedback;
  private int mySelectionMode;
  private boolean mySelectBackground;

  public MarqueeTracker() {
    setDefaultCursor(Cursors.CROSS);
    setDisabledCursor(Cursors.getNoCursor());
  }

  /**
   * Set whether the background should be selected if none of its children are included.
   */
  public void setSelectBackground(boolean selectBackground) {
    mySelectBackground = selectBackground;
  }

  @Override
  protected void handleButtonDown(int button) {
    if (button == MouseEvent.BUTTON1 || button == MouseEvent.BUTTON2) {
      if (myState == STATE_INIT) {
        myState = STATE_DRAG;

        if (myInputEvent.isControlDown()) {
          mySelectionMode = TOGGLE_MODE;
        }
        else if (myInputEvent.isShiftDown()) {
          mySelectionMode = APPEND_MODE;
        }
      }
    }
    else {
      myState = STATE_INVALID;
      eraseFeedback();
    }
    if (button != MouseEvent.BUTTON3) {
      refreshCursor();
    }
  }

  @Override
  protected void handleButtonUp(int button) {
    if (myState == STATE_DRAG_IN_PROGRESS) {
      myState = STATE_NONE;
      eraseFeedback();
      performMarqueeSelect();
    }
    else if (mySelectBackground) {
      performMarqueeSelect();
    }
  }

  @Override
  protected void handleDragInProgress() {
    if (myState == STATE_DRAG) {
      myState = STATE_DRAG_IN_PROGRESS;
      refreshCursor();
    }
    if (myState == STATE_DRAG_IN_PROGRESS) {
      showFeedback();
    }
  }

  @Override
  public void keyPressed(KeyEvent event, EditableArea area) throws Exception {
    boolean changedModifiers = event.getModifiers() != myModifiers;
    super.keyPressed(event, area);

    if (changedModifiers) {
      showFeedback();
    }
  }

  @Override
  public void keyReleased(KeyEvent event, EditableArea area) throws Exception {
    boolean changedModifiers = event.getModifiers() != myModifiers;
    super.keyReleased(event, area);

    if (changedModifiers) {
      showFeedback();
    }
  }

  @Override
  public void deactivate() {
    if (myState == STATE_DRAG_IN_PROGRESS) {
      eraseFeedback();
    }
    super.deactivate();
  }

  @Override
  protected Cursor calculateCursor() {
    if (myState == STATE_DRAG_IN_PROGRESS) {
      return getDefaultCursor();
    }
    if (myState == STATE_INVALID) {
      return getDisabledCursor();
    }
    return null;
  }

  private void showFeedback() {
    FeedbackLayer layer = myArea.getFeedbackLayer();

    if (myFeedback == null) {
      myFeedback = new AlphaFeedback(myColor);
      layer.add(myFeedback);
    }

    myFeedback.setBounds(getSelectionRectangle());
    if (ApplicationManager.getApplication().isInternal()) {
      Dimension size = myFeedback.getSize();
      myArea.setDescription("Size [" + size.width + " : " + size.height + "]");
    }
    layer.repaint();
  }

  protected void eraseFeedback() {
    if (myFeedback != null) {
      FeedbackLayer layer = myArea.getFeedbackLayer();
      layer.remove(myFeedback);
      layer.repaint();
      myFeedback = null;
    }
  }

  private Rectangle getSelectionRectangle() {
    if (isAltOptionPressed()) {
      // Alt/Option: Center selection around starting point
      int deltaX = Math.abs(myStartScreenX - myCurrentScreenX);
      int deltaY = Math.abs(myStartScreenY - myCurrentScreenY);
      return new Rectangle(myStartScreenX - deltaX, myStartScreenY - deltaY, 2 * deltaX, 2 * deltaY);
    }

    // Select diagonally from upper left to lower right
    return new Rectangle(myStartScreenX, myStartScreenY, 0, 0).union(new Rectangle(myCurrentScreenX, myCurrentScreenY, 0, 0));
  }

  private void performMarqueeSelect() {
    final Rectangle selectionRectangle = getSelectionRectangle();
    final List<RadComponent> newSelection = new ArrayList<>();
    RadComponent rootComponent = myArea.getRootComponent();

    if (rootComponent != null) {
      rootComponent.accept(new RadComponentVisitor() {
        @Override
        public void endVisit(RadComponent component) {
          if (selectionRectangle.contains(component.getBounds(myArea.getNativeComponent())) && !component.isBackground()) {
            newSelection.add(component);
          }
        }
      }, true);

      if (newSelection.isEmpty() && mySelectBackground) {
        rootComponent.accept(new RadComponentVisitor() {
          @Override
          public void endVisit(RadComponent component) {
            // Only select the bottom-most background
            if (newSelection.isEmpty() &&
                component.getBounds(myArea.getNativeComponent()).contains(selectionRectangle.x, selectionRectangle.y) &&
                component.isBackground()) {
              newSelection.add(component);
            }
          }
        }, true);
      }
    }

    if (mySelectionMode == TOGGLE_MODE) {
      List<RadComponent> selection = new ArrayList<>(myArea.getSelection());

      for (RadComponent component : newSelection) {
        int index = selection.indexOf(component);

        if (index == -1) {
          selection.add(component);
        }
        else {
          selection.remove(index);
        }
      }

      myArea.setSelection(selection);
    }
    else if (mySelectionMode == APPEND_MODE) {
      for (RadComponent component : newSelection) {
        myArea.appendSelection(component);
      }
    }
    else {
      myArea.setSelection(newSelection);
    }
  }
}