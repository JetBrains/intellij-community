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

import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadComponentVisitor;
import com.intellij.designer.designSurface.feedbacks.AlphaComponent;
import com.intellij.designer.utils.Cursors;

import javax.swing.*;
import java.awt.*;
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

  public MarqueeTracker() {
    setDefaultCursor(Cursors.CROSS);
    setDisabledCursor(Cursors.getNoCursor());
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
    refreshCursor();
  }

  @Override
  protected void handleButtonUp(int button) {
    if (myState == STATE_DRAG_IN_PROGRESS) {
      myState = STATE_NONE;
      eraseFeedback();
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
    else if (myState == STATE_INVALID) {
      return getDisabledCursor();
    }
    else {
      return null;
    }
  }

  private void showFeedback() {
    FeedbackLayer layer = myArea.getFeedbackLayer();

    if (myFeedback == null) {
      myFeedback = new AlphaComponent(myColor);
      layer.add(myFeedback);
    }

    myFeedback.setBounds(getSelectionRectangle());
    layer.repaint();
  }

  private void eraseFeedback() {
    if (myFeedback != null) {
      FeedbackLayer layer = myArea.getFeedbackLayer();
      layer.remove(myFeedback);
      layer.repaint();
      myFeedback = null;
    }
  }

  private Rectangle getSelectionRectangle() {
    return new Rectangle(myStartScreenX, myStartScreenY, 0, 0).union(new Rectangle(myCurrentScreenX, myCurrentScreenY, 0, 0));
  }

  private void performMarqueeSelect() {
    final Rectangle selectionRectangle = getSelectionRectangle();
    final List<RadComponent> newSelection = new ArrayList<RadComponent>();
    RadComponent rootComponent = myArea.getRootComponent();

    if (rootComponent != null) {
      rootComponent.accept(new RadComponentVisitor() {
        @Override
        public void endVisit(RadComponent component) {
          if (selectionRectangle.contains(component.getBounds(myArea.getNativeComponent()))) {
            newSelection.add(component);
          }
        }
      }, true);
    }

    if (mySelectionMode == TOGGLE_MODE) {
      List<RadComponent> selection = new ArrayList<RadComponent>(myArea.getSelection());

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