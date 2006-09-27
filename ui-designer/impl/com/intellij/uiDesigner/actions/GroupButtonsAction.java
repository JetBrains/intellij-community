package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import com.intellij.uiDesigner.radComponents.RadButtonGroup;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.propertyInspector.properties.IdentifierValidator;
import com.intellij.uiDesigner.UIDesignerBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class GroupButtonsAction extends AbstractGuiEditorAction {
  protected void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e) {
    groupButtons(editor, selection);
  }

  public static void groupButtons(final GuiEditor editor, final List<RadComponent> selectedComponents) {
    if (!editor.ensureEditable()) return;
    String groupName = Messages.showInputDialog(editor.getProject(),
                                                UIDesignerBundle.message("group.buttons.name.prompt"),
                                                UIDesignerBundle.message("group.buttons.title"),
                                                Messages.getQuestionIcon(),
                                                editor.getRootContainer().suggestGroupName(),
                                                new IdentifierValidator(editor.getProject()));
    if (groupName == null) return;
    RadRootContainer rootContainer = editor.getRootContainer();
    RadButtonGroup group = rootContainer.createGroup(groupName);
    for(RadComponent component: selectedComponents) {
      rootContainer.setGroupForComponent(component, group);
    }
    editor.refreshAndSave(true);
  }

  protected void update(@NotNull GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    e.getPresentation().setVisible(allButtons(selection));
    e.getPresentation().setEnabled(allButtons(selection) && selection.size() >= 2 &&
                                   !UngroupButtonsAction.isSameGroup(editor, selection));
  }

  public static boolean allButtons(final ArrayList<RadComponent> selection) {
    for(RadComponent component: selection) {
      if (!(component.getDelegee() instanceof AbstractButton) ||
          component.getDelegee() instanceof JButton) {
        return false;
      }
    }
    return true;
  }
}
