/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.OrderPanel;
import com.intellij.ui.OrderPanelListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * @author alex
 */
public class SelectFilesDialog extends DialogWrapper implements ActionListener {

  private final String[] myFiles;
  private FilesList myFilesList;
  private JButton mySelectAllButton;
  private JButton myDeselectAllButton;
  private final String myLabel;
  private final String myHelpID;

  public SelectFilesDialog(final Project project, String label, String title, String actionName, String[] paths,
                           @NonNls String helpID) {
    super(project, true);
    myHelpID = helpID;
    setOKButtonText(actionName);
    setTitle(title);
    setResizable(true);

    myFiles = paths;
    myLabel = label;
    getHelpAction().setEnabled(myHelpID != null);

    init();
  }

  protected void doHelpAction() {
    if (myHelpID != null) {
      HelpManager.getInstance().invokeHelp(myHelpID);
    }
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void init() {
    super.init();

    mySelectAllButton.addActionListener(this);
    myDeselectAllButton.addActionListener(this);

    myFilesList.addListener(new OrderPanelListener() {
      public void entryMoved() {
        getOKAction().setEnabled(isOKActionEnabled());
      }
    });
  }

  protected String getDimensionServiceKey() {
    return "svn.selectFilesDialog";
  }

  public boolean shouldCloseOnCross() {
    return true;
  }

  public JComponent getPreferredFocusedComponent() {
    return myFilesList;
  }

  public boolean isOKActionEnabled() {
    return myFilesList.getSelectedPaths().length > 0;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout(5,5));

    JLabel label = new JLabel(myLabel);
    panel.add(label, BorderLayout.NORTH);

    myFilesList = new FilesList(myFiles);
    myFilesList.setCheckboxColumnName("");
    myFilesList.setEntriesEditable(false);
    for (String myFile : myFiles) {
      myFilesList.add(myFile);
    }
    Font font = myFilesList.getFont();
    FontMetrics fm = myFilesList.getFontMetrics(font);
    int height = fm.getHeight();
    myFilesList.setPreferredSize(new Dimension(myFilesList.getPreferredSize().width, height*7));
    panel.add(ScrollPaneFactory.createScrollPane(myFilesList), BorderLayout.CENTER);

    JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    mySelectAllButton = new JButton(SvnBundle.message("button.text.select.all"));
    myDeselectAllButton = new JButton(SvnBundle.message("button.text.deselect.all"));

    buttonsPanel.add(mySelectAllButton);
    buttonsPanel.add(myDeselectAllButton);

    panel.add(buttonsPanel, BorderLayout.SOUTH);
    return panel;
  }

  public void actionPerformed(ActionEvent e) {
    for (String file : myFiles) {
      myFilesList.setChecked(file, e.getSource() == mySelectAllButton);
    }
    myFilesList.refresh();
    setOKActionEnabled(isOKActionEnabled());
  }

  public String[] getSelectedPaths() {
    return myFilesList.getSelectedPaths();
  }

  private class FilesList extends OrderPanel<String> {

    private final Map<String, Boolean> mySelectedFiles;

    protected FilesList(String[] files) {
      super(String.class, true);
      mySelectedFiles = new TreeMap<>();
      for (String file : files) {
        mySelectedFiles.put(file, Boolean.TRUE);
      }
    }
    public boolean isCheckable(final String entry) {
      return true;
    }

    public boolean isChecked(final String entry) {
      return Boolean.TRUE.equals(mySelectedFiles.get(entry));
    }

    public void setChecked(final String entry, final boolean checked) {
      mySelectedFiles.put(entry, checked);
      getOKAction().setEnabled(isOKActionEnabled());
    }

    public void refresh() {
      clear();
      for (String path : mySelectedFiles.keySet()) {
        add(path);
      }
    }

    public String[] getSelectedPaths() {
      Collection<String> selected = new TreeSet<>();
      for (String path : mySelectedFiles.keySet()) {
        if (isChecked(path)) {
          selected.add(path);
        }
      }
      return ArrayUtil.toStringArray(selected);
    }
  }
}
