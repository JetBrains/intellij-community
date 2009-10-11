/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.uiDesigner.componentTree;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.uiDesigner.ErrorAnalyzer;
import com.intellij.uiDesigner.ErrorInfo;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.quickFixes.QuickFixManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import javax.swing.*;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class QuickFixManagerImpl extends QuickFixManager<ComponentTree>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.componentTree.QuickFixManagerImpl");

  public QuickFixManagerImpl(final GuiEditor editor, @NotNull final ComponentTree componentTree, final JViewport viewPort) {
    super(editor, componentTree, viewPort);
    myComponent.addTreeSelectionListener(new MyTreeSelectionListener());
  }

  @NotNull
  protected ErrorInfo[] getErrorInfos() {
    final RadComponent component = myComponent.getSelectedComponent();
    if(component == null){
      return ErrorInfo.EMPTY_ARRAY;
    }
    return ErrorAnalyzer.getAllErrorsForComponent(component);
  }

  public Rectangle getErrorBounds() {
    final TreePath selectionPath = myComponent.getSelectionPath();
    LOG.assertTrue(selectionPath != null);
    return myComponent.getPathBounds(selectionPath);
  }

  private final class MyTreeSelectionListener implements TreeSelectionListener{
    public void valueChanged(final TreeSelectionEvent e) {
      hideIntentionHint();
      updateIntentionHintVisibility();

      ErrorInfo[] errorInfos = getErrorInfos();
      final StatusBar statusBar = WindowManager.getInstance().getStatusBar(myComponent.getProject());
      if (errorInfos.length > 0 && errorInfos [0].myDescription != null) {
        statusBar.setInfo(errorInfos [0].myDescription);
      }
      else {
        statusBar.setInfo("");
      }
    }
  }
}