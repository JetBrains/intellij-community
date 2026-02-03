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
package com.intellij.designer.designSurface.tools;

import com.intellij.designer.model.RadComponent;

/**
 * @author Alexander Lobas
 */
public class MarqueeSelectionTracker extends MarqueeTracker {
  private final RadComponent myComponent;
  private boolean mySelected;

  public MarqueeSelectionTracker(RadComponent component) {
    myComponent = component;
  }

  @Override
  protected void handleButtonUp(int button) {
    if (myState == STATE_DRAG) {
      myState = STATE_NONE;
      eraseFeedback();
      performSelection();
    }
    else {
      super.handleButtonUp(button);
    }
  }

  private void performSelection() {
    if (mySelected || myArea.isTree()) {
      return;
    }
    mySelected = true;
    SelectionTracker.performSelection(this, myComponent);
  }
}