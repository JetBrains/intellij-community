/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.designer.designSurface.feedbacks.AlphaFeedback;
import com.intellij.designer.model.RadComponent;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public abstract class DropToOperation extends AbstractEditOperation {
  private final Color myColor;
  private JComponent myFeedback;

  public DropToOperation(RadComponent container, OperationContext context, Color color) {
    super(container, context);
    myColor = color;
  }

  @Override
  public void showFeedback() {
    FeedbackLayer layer = myContext.getArea().getFeedbackLayer();

    if (myFeedback == null) {
      myFeedback = new AlphaFeedback(myColor);
      layer.add(myFeedback);
      myFeedback.setBounds(myContainer.getBounds(layer));
      layer.repaint();
    }
  }

  @Override
  public void eraseFeedback() {
    if (myFeedback != null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      layer.remove(myFeedback);
      layer.repaint();
      myFeedback = null;
    }
  }
}