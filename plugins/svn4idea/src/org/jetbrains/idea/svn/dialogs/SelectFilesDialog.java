// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.OrderPanel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
  private final @NlsContexts.Label String myLabel;
  private final @NonNls String myHelpID;

  public SelectFilesDialog(Project project,
                           @NlsContexts.Label String label,
                           @NlsContexts.DialogTitle String title,
                           @NlsContexts.Button @NotNull String actionName,
                           String[] paths,
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

  @Nullable
  @Override
  protected String getHelpId() {
    return myHelpID;
  }

  @Override
  protected void init() {
    super.init();

    mySelectAllButton.addActionListener(this);
    myDeselectAllButton.addActionListener(this);

    myFilesList.addListener(() -> getOKAction().setEnabled(isOKActionEnabled()));
  }

  @Override
  protected String getDimensionServiceKey() {
    return "svn.selectFilesDialog";
  }

  @Override
  public boolean shouldCloseOnCross() {
    return true;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myFilesList;
  }

  @Override
  public boolean isOKActionEnabled() {
    return myFilesList.getSelectedPaths().length > 0;
  }

  @Override
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

  @Override
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
    @Override
    public boolean isCheckable(final String entry) {
      return true;
    }

    @Override
    public boolean isChecked(final String entry) {
      return Boolean.TRUE.equals(mySelectedFiles.get(entry));
    }

    @Override
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
      return ArrayUtilRt.toStringArray(selected);
    }
  }
}
