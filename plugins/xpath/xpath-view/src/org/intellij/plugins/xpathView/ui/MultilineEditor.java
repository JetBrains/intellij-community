/*
 * Copyright 2002-2005 Sascha Weinreuter
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
package org.intellij.plugins.xpathView.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.EditorTextField;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;

public class MultilineEditor extends JPanel {

    private final EditorModel myModel;
    private final EditorTextField myEditorTextField;

    public EditorTextField getField() {
    return myEditorTextField;
  }

  public interface EditorModel extends ListModel {
        int getSelectedIndex();

        void setSelectedIndex(int index);

        String getItemString(int index);

        @Override
        int getSize();
    }

    private static abstract class ItemAction extends AnAction {
        ItemAction(String id, JComponent component) {
            ActionUtil.copyFrom(this, id);
            registerCustomShortcutSet(getShortcutSet(), component);
        }
    }

    public MultilineEditor(Document document, Project project, FileType fileType, EditorModel model) {
        super(new BorderLayout());
        this.myModel = model;
        myEditorTextField = new EditorTextField(document, project, fileType) {
          @Override
          protected @NotNull EditorEx createEditor() {
              final EditorEx editor = super.createEditor();

              editor.setHorizontalScrollbarVisible(true);
              editor.setVerticalScrollbarVisible(true);
              editor.setEmbeddedIntoDialogWrapper(true);

              editor.getComponent().setPreferredSize(null);

              return editor;
          }

          @Override
          protected boolean isOneLineMode() {
              return false;
          }
        };
        add(myEditorTextField, BorderLayout.CENTER);
        model.addListDataListener(new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent e) {
            }

            @Override
            public void intervalRemoved(ListDataEvent e) {
            }

            @Override
            public void contentsChanged(ListDataEvent e) {
                final int selectedIndex = myModel.getSelectedIndex();
                if (selectedIndex != -1) {
                    myEditorTextField.setText(myModel.getItemString(selectedIndex));
                } else {
//                    setText("");
                }
            }
        });
        addHistoryPagers();
    }

    private void addHistoryPagers() {
        final DefaultActionGroup pagerGroup = new DefaultActionGroup();
        pagerGroup.add(new ItemAction("PreviousOccurence", this) {
            @Override
            public void update(@NotNull AnActionEvent e) {
                final Presentation presentation = e.getPresentation();
                presentation.setEnabled(myModel.getSelectedIndex() < myModel.getSize() - 1);
                presentation.setText(XPathBundle.message("action.previous.history.entry.text"));
                presentation.setDescription(XPathBundle.message("action.navigate.to.previous.history.entry.description"));
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                myModel.setSelectedIndex(myModel.getSelectedIndex() + 1);
                refocus();

            }
        });
        pagerGroup.add(new ItemAction("NextOccurence", this) {
            @Override
            public void update(@NotNull AnActionEvent e) {
                final Presentation presentation = e.getPresentation();
                presentation.setEnabled(myModel.getSelectedIndex() > 0);
                presentation.setText(XPathBundle.message("action.next.history.entry.text"));
                presentation.setDescription(XPathBundle.message("action.navigate.to.next.history.entry.description"));
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                myModel.setSelectedIndex(myModel.getSelectedIndex() - 1);
                refocus();
            }
        });
        final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("HistoryPager", pagerGroup, false);
        add(toolbar.getComponent(), BorderLayout.EAST);
    }

    private void refocus() {
        SwingUtilities.invokeLater(() -> {
            final Editor editor = myEditorTextField.getEditor();
            if (editor != null) {
              IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(editor.getContentComponent(), true));
            }
            myEditorTextField.selectAll();
        });
    }

}