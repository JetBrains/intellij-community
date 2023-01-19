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
package com.intellij.designer.actions;

import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Alexander Lobas
 */
public class StartInplaceEditing extends AnAction {
  private DesignerEditorPanel myDesigner;

  public void setDesignerPanel(@Nullable DesignerEditorPanel designer) {
    myDesigner = designer;
  }


  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    boolean enabled = false;

    if (myDesigner != null) {
      List<RadComponent> selection = myDesigner.getSurfaceArea().getSelection();
      if (selection.size() == 1) {
        try {
          enabled = !selection.get(0).getInplaceProperties().isEmpty();
        }
        catch (Exception e) {
        }
      }
    }

    event.getPresentation().setEnabled(enabled);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myDesigner.getInplaceEditingLayer().startEditing(null);
  }
}