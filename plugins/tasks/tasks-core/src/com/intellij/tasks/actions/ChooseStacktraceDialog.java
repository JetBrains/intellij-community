/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.tasks.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.tasks.Comment;
import com.intellij.tasks.Task;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.unscramble.AnalyzeStacktraceUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Date;

/**
 * @author Dmitry Avdeev
 */
public class ChooseStacktraceDialog extends DialogWrapper {

  private JList myList;
  private JPanel myPanel;
  private JPanel myEditorPanel;
  private AnalyzeStacktraceUtil.StacktraceEditorPanel myEditor;

  public ChooseStacktraceDialog(Project project, final Task issue) {
    super(project, false);

    setTitle("Choose Stacktrace to Analyze");
    Comment[] comments = issue.getComments();
    ArrayList<Comment> list = new ArrayList<Comment>(comments.length + 1);
    final String description = issue.getDescription();
    if (description != null) {
      list.add(new Description(description));
    }
    ContainerUtil.addAll(list, comments);

    myList.setModel(new CollectionListModel<Comment>(list));
    myList.setCellRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof Description) {
          append("Description");
        }
        else {
          append("Commented by " + ((Comment)value).getAuthor() + " (" + ((Comment)value).getDate() + ")");
        }
      }
    });

    myEditor = AnalyzeStacktraceUtil.createEditorPanel(project, myDisposable);
    myEditorPanel.add(myEditor, BorderLayout.CENTER);
    myList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        Object value = myList.getSelectedValue();
        if (value instanceof Comment) {
          myEditor.setText(((Comment)value).getText());
        }
        else {
          myEditor.setText("");
        }
      }
    });
    myList.setSelectedValue(list.get(0), false);
    init();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myList;
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public Comment[] getTraces() {
    return ArrayUtil.toObjectArray(Comment.class, myList.getSelectedValues());
  }

  private static class Description extends Comment {
    private final String myDescription;

    public Description(String description) {
      myDescription = description;
    }

    @Override
    public String getText() {
      return myDescription;
    }

    @Override
    public String getAuthor() {
      return null;
    }

    @Override
    public Date getDate() {
      return null;
    }
  }
}
