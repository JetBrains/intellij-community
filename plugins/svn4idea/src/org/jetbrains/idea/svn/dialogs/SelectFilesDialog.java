// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.OrderPanel;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

/**
 * @author alex
 */
public class SelectFilesDialog extends DialogWrapper implements ActionListener {

  private final @NotNull SortedSet<String> myPaths;
  private FilesList myFilesList;
  private JButton mySelectAllButton;
  private JButton myDeselectAllButton;

  public SelectFilesDialog(@NotNull Project project,
                           @NotNull SortedSet<String> paths) {
    super(project, true);
    myPaths = paths;

    setOKButtonText(SvnBundle.message("action.name.mark.resolved"));
    setTitle(SvnBundle.message("dialog.title.mark.resolved"));
    setResizable(true);
    getHelpAction().setEnabled(true);

    init();
  }

  @Override
  protected @NotNull String getHelpId() {
    return "vcs.subversion.resolve";
  }

  @Override
  protected void init() {
    super.init();

    mySelectAllButton.addActionListener(this);
    myDeselectAllButton.addActionListener(this);
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
    return !getSelectedPaths().isEmpty();
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout(5,5));

    JLabel label = new JLabel(SvnBundle.message("label.select.files.and.directories.to.mark.resolved"));
    panel.add(label, BorderLayout.NORTH);

    myFilesList = new FilesList(myPaths);
    int height = myFilesList.getFontMetrics(myFilesList.getFont()).getHeight();
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
    for (String file : myPaths) {
      myFilesList.setChecked(file, e.getSource() == mySelectAllButton);
    }
    myFilesList.clear();
    myFilesList.init();
    setOKActionEnabled(isOKActionEnabled());
  }

  public @NotNull SortedSet<String> getSelectedPaths() {
    return myFilesList.getSelectedPaths();
  }

  private class FilesList extends OrderPanel<String> {

    private final Map<String, Boolean> mySelectedFiles = new TreeMap<>();

    private FilesList(@NotNull SortedSet<String> paths) {
      super(String.class);
      for (String path : paths) {
        mySelectedFiles.put(path, Boolean.TRUE);
      }
      init();
    }

    @Override
    public boolean isChecked(@NotNull String entry) {
      return isChecked(mySelectedFiles.get(entry));
    }

    @Override
    public void setChecked(@NotNull String entry, boolean checked) {
      mySelectedFiles.put(entry, checked);
      getOKAction().setEnabled(isOKActionEnabled());
    }

    private void init() {
      addAll(mySelectedFiles.keySet());
    }

    private @NotNull SortedSet<String> getSelectedPaths() {
      TreeSet<String> selected = new TreeSet<>();
      for (Map.Entry<String, Boolean> entry : mySelectedFiles.entrySet()) {
        if (isChecked(entry.getValue())) {
          selected.add(entry.getKey());
        }
      }
      return Collections.unmodifiableSortedSet(selected);
    }

    private static boolean isChecked(@Nullable Boolean value) {
      return Boolean.TRUE.equals(value);
    }
  }
}
