/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.debugger.containerview;

import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

class PyDataViewTabs extends JBRunnerTabs {
  public PyDataViewTabs(@NotNull Project project) {
    super(project, ActionManager.getInstance(), IdeFocusManager.findInstance(), project);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (isEmptyVisible()) {
      drawCenteredString(g, PyDataViewToolWindowFactory.EMPTY_TEXT);
    }
  }

  private static void drawCenteredString(Graphics g, String text) {
    Rectangle rect = g.getClipBounds();
    FontMetrics metrics = g.getFontMetrics();
    int x = (rect.width - metrics.stringWidth(text)) / 2;
    int y = ((rect.height - metrics.getHeight()) / 2) + metrics.getAscent();
    g.drawString(text, x, y);
  }
}
