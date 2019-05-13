/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.toolWindowWithActions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.impl.ContentImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * API to work with toolwindow. It creates {@link ToolWindow} at the bottom of the screen, gives you API to add components there
 * and closes it (if needed).
 *
 * @author Ilya.Kazakevich
 */
final class ToolWindowApi {
  @NotNull
  private final ContentManager myContentManager;
  @NotNull
  private final String myWindowName;
  @NotNull
  private final ToolWindowManager myToolWindowManager;

  /**
   * @param project    project
   * @param windowName name to be used as id (and shown to user)
   */
  ToolWindowApi(@NotNull final Project project, @NotNull final String windowName) {

    myToolWindowManager = ToolWindowManager.getInstance(project);
    ToolWindow window = myToolWindowManager.getToolWindow(windowName);
    if (window == null) {
      window = myToolWindowManager.registerToolWindow(windowName, true, ToolWindowAnchor.BOTTOM);
    }
    window.activate(null);

    myContentManager = window.getContentManager();
    myContentManager.removeAllContents(true);
    myWindowName = windowName;
  }

  /**
   * @param component component to add to window
   */
  void add(@NotNull final JComponent component) {
    myContentManager.addContent(new ContentImpl(component, "", true));
  }

  /**
   * Close window
   */
  void close() {
    myContentManager.removeAllContents(true);
    myToolWindowManager.unregisterToolWindow(myWindowName);
  }
}
