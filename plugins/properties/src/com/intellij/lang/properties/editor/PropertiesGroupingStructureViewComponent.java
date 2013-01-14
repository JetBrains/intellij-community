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
package com.intellij.lang.properties.editor;

import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.structureView.GroupByWordPrefixes;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NonNls;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author cdr
 */
public class PropertiesGroupingStructureViewComponent extends StructureViewComponent {
  protected PropertiesGroupingStructureViewComponent(Project project,
                                                  FileEditor editor,
                                                  PropertiesGroupingStructureViewModel structureViewModel) {
    super(editor, structureViewModel, project);
    showToolbar();
  }

  @Override
  protected void addGroupByActions(DefaultActionGroup result) {
    super.addGroupByActions(result);
    result.add(new ChangeGroupSeparatorAction());
  }

  private class ChangeGroupSeparatorAction extends DefaultActionGroup {
    // separator -> presentable text
    private final Map<String,String> myPredefinedSeparators = new LinkedHashMap<String, String>();

    public ChangeGroupSeparatorAction() {
      super("Group by: ", true);
      myPredefinedSeparators.put(".", ".");
      myPredefinedSeparators.put("_", "__");
      myPredefinedSeparators.put("/", "/");
      String currentSeparator = getCurrentSeparator();
      if (!myPredefinedSeparators.containsKey(currentSeparator)) {
        myPredefinedSeparators.put(currentSeparator, currentSeparator);
      }
      refillActionGroup();
    }

    public final void update(AnActionEvent e) {
      String separator = getCurrentSeparator();
      Presentation presentation = e.getPresentation();
      presentation.setText("Group by: " + myPredefinedSeparators.get(separator));
    }

    private String getCurrentSeparator() {
      return ((PropertiesGroupingStructureViewModel)getTreeModel()).getSeparator();
    }

    private void refillActionGroup() {
      removeAll();
      for (final String separator : myPredefinedSeparators.keySet()) {
        if (separator.equals(getCurrentSeparator())) continue;
        String presentableText = myPredefinedSeparators.get(separator);
        add(new AnAction(presentableText) {

          @Override
          public void actionPerformed(AnActionEvent e) {
            ((PropertiesGroupingStructureViewModel)getTreeModel()).setSeparator(separator);
            setActionActive(GroupByWordPrefixes.ID, true);
            refillActionGroup();
            rebuild();
          }
        });
      }
      add(new SelectSeparatorAction());
    }

    private final class SelectSeparatorAction extends AnAction {

      public SelectSeparatorAction() {
        super(PropertiesBundle.message("select.separator.action.with.empty.separator.name"));
      }

      public final void actionPerformed(AnActionEvent e) {
        String[] strings = myPredefinedSeparators.keySet().toArray(new String[myPredefinedSeparators.size()]);
        String current = getCurrentSeparator();
        String separator = Messages.showEditableChooseDialog(PropertiesBundle.message("select.property.separator.dialog.text"),
                                                             PropertiesBundle.message("select.property.separator.dialog.title"),
                                                             Messages.getQuestionIcon(),
                                                             strings, current, null);
        if (separator == null) {
          return;
        }
        myPredefinedSeparators.put(separator, separator);
        refillActionGroup();
      }
    }
  }

  @NonNls
  public String getHelpID() {
    return "editing.propertyFile.bundleEditor";
  }
}

