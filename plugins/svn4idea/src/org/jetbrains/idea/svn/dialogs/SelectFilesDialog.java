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
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.OrderPanel;
import com.intellij.ui.OrderPanelListener;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.svn.SvnBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 12.07.2005
 * Time: 18:59:52
 * To change this template use File | Settings | File Templates.
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
    for (int i = 0; i < myFiles.length; i++) {
      myFilesList.add(myFiles[i]);
    }
    Font font = myFilesList.getFont();
    FontMetrics fm = myFilesList.getFontMetrics(font);
    int height = fm.getHeight();
    myFilesList.setPreferredSize(new Dimension(myFilesList.getPreferredSize().width, height*7));
    panel.add(new JScrollPane(myFilesList), BorderLayout.CENTER);

    JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    mySelectAllButton = new JButton(SvnBundle.message("button.text.select.all"));
    myDeselectAllButton = new JButton(SvnBundle.message("button.text.deselect.all"));

    buttonsPanel.add(mySelectAllButton);
    buttonsPanel.add(myDeselectAllButton);

    panel.add(buttonsPanel, BorderLayout.SOUTH);
    return panel;
  }

  public void actionPerformed(ActionEvent e) {
    for (int i = 0; i < myFiles.length; i++) {
      myFilesList.setChecked(myFiles[i], e.getSource() == mySelectAllButton);
    }
    myFilesList.refresh();
    setOKActionEnabled(isOKActionEnabled());
  }

  public String[] getSelectedPaths() {
    return myFilesList.getSelectedPaths();
  }

  private class FilesList extends OrderPanel {

    private final Map mySelectedFiles;

    protected FilesList(String[] files) {
      super(String.class, true);
      mySelectedFiles = new TreeMap();
      for (int i = 0; i < files.length; i++) {
        mySelectedFiles.put(files[i], Boolean.TRUE);
      }
    }
    public boolean isCheckable(final Object entry) {
      return true;
    }
    public boolean isChecked(final Object entry) {
      return Boolean.TRUE.equals(mySelectedFiles.get(entry));
    }
    public void setChecked(final Object entry, final boolean checked) {
      mySelectedFiles.put(entry, checked ? Boolean.TRUE : Boolean.FALSE);
      getOKAction().setEnabled(isOKActionEnabled());
    }

    public void refresh() {
      clear();
      for (Iterator paths = mySelectedFiles.keySet().iterator(); paths.hasNext();) {
        String path = (String)paths.next();
        add(path);
      }
    }

    public String[] getSelectedPaths() {
      Collection selected = new TreeSet();
      for (Iterator paths = mySelectedFiles.keySet().iterator(); paths.hasNext();) {
        String path = (String)paths.next();
        if (isChecked(path)) {
          selected.add(path);
        }
      }
      return (String[])ArrayUtil.toStringArray(selected);
    }
  }
}
