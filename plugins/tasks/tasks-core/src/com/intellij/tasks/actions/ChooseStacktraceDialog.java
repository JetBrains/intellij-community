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
import com.intellij.tasks.TaskBundle;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.unscramble.AnalyzeStacktraceUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;

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

  private JList<Comment> myList;
  private JPanel myPanel;
  private JPanel myEditorPanel;
  private final AnalyzeStacktraceUtil.StacktraceEditorPanel myEditor;

  public ChooseStacktraceDialog(Project project, final Task issue) {
    super(project, false);

    setTitle(TaskBundle.message("dialog.title.choose.stacktrace.to.analyze"));
    Comment[] comments = issue.getComments();
    ArrayList<Comment> list = new ArrayList<>(comments.length + 1);
    final String description = issue.getDescription();
    if (description != null) {
      list.add(new Description(description));
    }
    ContainerUtil.addAll(list, comments);

    myList.setModel(new CollectionListModel<>(list));
    myList.setCellRenderer(SimpleListCellRenderer.create("", o ->
      o instanceof Description ? TaskBundle.message("label.description") :
      TaskBundle.message("label.commented.by", o.getAuthor(), o.getDate())));
    myEditor = AnalyzeStacktraceUtil.createEditorPanel(project, myDisposable);
    myEditorPanel.add(myEditor, BorderLayout.CENTER);
    myList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        Comment value = myList.getSelectedValue();
        myEditor.setText(value != null ? value.getText() : "");
      }
    });
    myList.setSelectedValue(list.get(0), false);
    init();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myList;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public Comment[] getTraces() {
    return ArrayUtil.toObjectArray(Comment.class, myList.getSelectedValuesList());
  }

  private static class Description extends Comment {
    private final @Nls String myDescription;

    Description(@Nls String description) {
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
