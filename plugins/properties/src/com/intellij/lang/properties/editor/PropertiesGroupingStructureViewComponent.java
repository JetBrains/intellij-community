// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.editor;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.structureView.GroupByWordPrefixes;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.EmptyIcon;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author cdr
 */
public class PropertiesGroupingStructureViewComponent extends StructureViewComponent {

  protected PropertiesGroupingStructureViewComponent(Project project,
                                                  FileEditor editor,
                                                  PropertiesGroupingStructureViewModel structureViewModel) {
    super(editor, structureViewModel, project, true);
    showToolbar();
  }

  @Override
  protected void addGroupByActions(DefaultActionGroup result) {
    super.addGroupByActions(result);
    result.add(new ChangeGroupSeparatorAction());
    if (getTreeModel() instanceof ResourceBundleStructureViewModel) {
      result.add(createSettingsActionGroup());
    }
  }

  private ActionGroup createSettingsActionGroup() {
    DefaultActionGroup actionGroup = new DefaultActionGroup(PropertiesBundle.message("resource.bundle.editor.settings.action.title"), true);
    final Presentation presentation = actionGroup.getTemplatePresentation();
    presentation.setIcon(AllIcons.General.GearPlain);
    actionGroup.add(new ResourceBundleEditorKeepEmptyValueToggleAction());

    actionGroup.add(new ToggleAction(PropertiesBundle.message("show.only.incomplete.action.text"), null, AllIcons.General.Error) {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return ((ResourceBundleStructureViewModel)getTreeModel()).isShowOnlyIncomplete();
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        ((ResourceBundleStructureViewModel)getTreeModel()).setShowOnlyIncomplete(state);
        rebuild();
      }
    });

    return actionGroup;
  }

  private class ChangeGroupSeparatorAction extends DefaultActionGroup {
    private final Set<String> myPredefinedSeparators = new LinkedHashSet<>();

    public ChangeGroupSeparatorAction() {
      super("Group by: ", true);
      myPredefinedSeparators.add(".");
      myPredefinedSeparators.add("_");
      myPredefinedSeparators.add("/");
      String currentSeparator = getCurrentSeparator();
      if (!myPredefinedSeparators.contains(currentSeparator)) {
        myPredefinedSeparators.add(currentSeparator);
      }
      refillActionGroup();
    }

    @Override
    public final void update(AnActionEvent e) {
      String separator = getCurrentSeparator();
      Presentation presentation = e.getPresentation();
      presentation.setText("Group by: " + separator, false);
      presentation.setIcon(EmptyIcon.ICON_16);
    }

    private String getCurrentSeparator() {
      return ((PropertiesGroupingStructureViewModel)getTreeModel()).getSeparator();
    }

    private void refillActionGroup() {
      removeAll();
      for (final String separator : myPredefinedSeparators) {
        if (separator.equals(getCurrentSeparator())) continue;
        AnAction action = new AnAction() {
          @Override
          public void actionPerformed(AnActionEvent e) {
            ((PropertiesGroupingStructureViewModel)getTreeModel()).setSeparator(separator);
            setActionActive(GroupByWordPrefixes.ID, true);
            refillActionGroup();
            rebuild();
          }
        };
        action.getTemplatePresentation().setText(separator, false);
        add(action);
      }
      add(new SelectSeparatorAction());
    }

    private final class SelectSeparatorAction extends AnAction {

      public SelectSeparatorAction() {
        super(PropertiesBundle.message("select.separator.action.with.empty.separator.name"));
      }

      @Override
      public final void actionPerformed(AnActionEvent e) {
        String[] strings = ArrayUtil.toStringArray(myPredefinedSeparators);
        String current = getCurrentSeparator();
        String separator = Messages.showEditableChooseDialog(PropertiesBundle.message("select.property.separator.dialog.text"),
                                                             PropertiesBundle.message("select.property.separator.dialog.title"),
                                                             Messages.getQuestionIcon(),
                                                             strings, current, null);
        if (separator == null) {
          return;
        }
        myPredefinedSeparators.add(separator);
        refillActionGroup();
      }
    }
  }
}

