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

import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Cursors;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class DragTracker extends SelectionTracker {
  private static final Cursor myDragCursor = Cursors.getMoveCursor();

  public DragTracker(RadComponent component) {
    super(component);
    setDefaultCursor(Cursors.RESIZE_ALL);
    setDisabledCursor(Cursors.getNoCursor());
  }

  @Override
  protected Cursor getDefaultCursor() {
    return myState == STATE_NONE ? super.getDefaultCursor() : myDragCursor;
  }
}