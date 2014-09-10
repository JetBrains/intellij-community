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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.*;
import com.intellij.uiDesigner.radComponents.RadComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class ResetValueAction extends AbstractGuiEditorAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.actions.ResetValueAction");

  protected void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e) {
    final PropertyInspectorTable inspector = PropertyInspectorTable.DATA_KEY.getData(e.getDataContext());
    assert inspector != null;
    final Property property = inspector.getSelectedProperty();
    assert property != null;
    doResetValue(selection, property, editor);
  }

  public static void doResetValue(final List<RadComponent> selection, final Property property, final GuiEditor editor) {
    try {
      if (!editor.ensureEditable()) return;
      final PropertyInspector propertyInspector = DesignerToolWindowManager.getInstance(editor).getPropertyInspector();
      if (propertyInspector.isEditing()) {
        propertyInspector.stopEditing();
      }
      //noinspection unchecked
      for(RadComponent component: selection) {
        //noinspection unchecked
        if (property.isModified(component)) {
          //noinspection unchecked
          property.resetValue(component);
          component.getDelegee().invalidate();
        }
      }
      editor.refreshAndSave(false);
      propertyInspector.repaint();
    }
    catch (Exception e1) {
      LOG.error(e1);
    }
  }

  protected void update(final GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    PropertyInspectorTable inspector = PropertyInspectorTable.DATA_KEY.getData(e.getDataContext());
    if (inspector != null) {
      final Property selectedProperty = inspector.getSelectedProperty();
      //noinspection unchecked
      e.getPresentation().setEnabled(selectedProperty != null &&
                                     selection.size() > 0 &&
                                     inspector.isModifiedForSelection(selectedProperty));
    }
    else {
      e.getPresentation().setEnabled(false);
    }
  }
}
