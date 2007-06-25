package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * @author max
 */
public class ChangeListChooser extends DialogWrapper {
  private LocalChangeList mySelectedList;
  private ChangeListChooserPanel myPanel;

  public ChangeListChooser(@NotNull Project project,
                           @NotNull Collection<? extends ChangeList> changelists,
                           @Nullable ChangeList defaultSelection,
                           final String title,
                           @Nullable final String defaultName) {
    super(project, false);

    myPanel = new ChangeListChooserPanel(project);
    myPanel.setChangeLists(changelists);
    myPanel.setDefaultSelection(defaultSelection);

    setTitle(title);
    if (defaultName != null) {
      myPanel.setDefaultName(defaultName);
    }

    init();
  }

  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPreferredFocusedComponent();
  }

  protected String getDimensionServiceKey() {
    return "VCS.ChangelistChooser";
  }

  protected void doOKAction() {
    mySelectedList = myPanel.getSelectedList();
    if (mySelectedList != null) {
      super.doOKAction();
    }
  }

  public LocalChangeList getSelectedList() {
    return mySelectedList;
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
