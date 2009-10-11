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

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.designSurface.InsertComponentProcessor;
import com.intellij.uiDesigner.designSurface.ComponentDropLocation;
import com.intellij.uiDesigner.designSurface.ComponentItemDragObject;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadTabbedPane;
import com.intellij.uiDesigner.palette.Palette;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class AddTabAction extends AbstractGuiEditorAction {
  public AddTabAction() {
    super(true);
  }

  protected void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e) {
    RadTabbedPane tabbedPane = (RadTabbedPane) selection.get(0);
    Palette palette = Palette.getInstance(editor.getProject());

    final RadComponent radComponent = InsertComponentProcessor.createPanelComponent(editor);
    final ComponentDropLocation dropLocation = tabbedPane.getDropLocation(null);
    dropLocation.processDrop(editor, new RadComponent[] { radComponent }, null, 
                             new ComponentItemDragObject(palette.getPanelItem()));
  }

  @Override protected void update(@NotNull GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    e.getPresentation().setVisible(selection.size() == 1 && selection.get(0) instanceof RadTabbedPane);
  }
}
