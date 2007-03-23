package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.InplaceContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class StartInplaceEditingAction extends AnAction{

  private GuiEditor myEditor;

  public StartInplaceEditingAction(@Nullable final GuiEditor editor) {
    myEditor = editor;
  }

  public void setEditor(final GuiEditor editor) {
    myEditor = editor;
  }

  public void actionPerformed(final AnActionEvent e) {
    final ArrayList<RadComponent> selection = FormEditingUtil.getAllSelectedComponents(myEditor);
    final RadComponent component = selection.get(0);
    final Property defaultInplaceProperty = component.getDefaultInplaceProperty();
    myEditor.getInplaceEditingLayer().startInplaceEditing(component, defaultInplaceProperty,
                                                          component.getDefaultInplaceEditorBounds(), new InplaceContext(true));
  }

  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final ArrayList<RadComponent> selection = FormEditingUtil.getAllSelectedComponents(myEditor);

    // Inplace editing can be started only if single component is selected
    if(selection.size() != 1){
      presentation.setEnabled(false);
      return;
    }

    // Selected component should have "inplace" property
    final RadComponent component = selection.get(0);
    presentation.setEnabled(component.getDefaultInplaceProperty() != null);
  }
}
