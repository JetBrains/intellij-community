// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.editor;

import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.structureView.GroupByWordPrefixes;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

public class PropertiesGroupingStructureViewComponent extends StructureViewComponent {

  protected PropertiesGroupingStructureViewComponent(Project project,
                                                  FileEditor editor,
                                                  PropertiesGroupingStructureViewModel structureViewModel) {
    super(editor, structureViewModel, project, true);
  }


  @Override
  public void setActionActive(String name, boolean state) {
    if (GroupByWordPrefixes.ID.equals(name)) {
      ((PropertiesGroupingStructureViewModel)getTreeModel()).setGroupingActive(state);
    }
    super.setActionActive(name, state);
  }

  @Override
  protected void addGroupByActions(@NotNull DefaultActionGroup result) {
    super.addGroupByActions(result);
    result.add(new ChangeGroupSeparatorAction());
  }

  private class ChangeGroupSeparatorAction extends DefaultActionGroup {
    private final Set<String> myPredefinedSeparators = new LinkedHashSet<>();

    ChangeGroupSeparatorAction() {
      super(PropertiesBundle.message("group.by.title"), true);
      myPredefinedSeparators.add(".");
      myPredefinedSeparators.add("_");
      myPredefinedSeparators.add("/");
      myPredefinedSeparators.add(getCurrentSeparator());
      refillActionGroup();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public final void update(@NotNull AnActionEvent e) {
      String separator = getCurrentSeparator();
      Presentation presentation = e.getPresentation();
      presentation.setText(PropertiesBundle.message("group.by.0", separator), false);
      presentation.setIcon(EmptyIcon.ICON_16);
    }

    private @NlsSafe String getCurrentSeparator() {
      return ((PropertiesGroupingStructureViewModel)getTreeModel()).getSeparator();
    }

    private void refillActionGroup() {
      removeAll();
      for (final @NlsSafe String separator : myPredefinedSeparators) {
        if (separator.equals(getCurrentSeparator())) continue;
        AnAction action = new AnAction() {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
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

      SelectSeparatorAction() {
        super(PropertiesBundle.messagePointer("select.separator.action.with.empty.separator.name"));
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        String[] strings = ArrayUtilRt.toStringArray(myPredefinedSeparators);
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

