package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Collection;

/**
 * @author max
 */
public class ChangeListChooser extends DialogWrapper {
  private JPanel myPanel;
  private JRadioButton myRbExisting;
  private JRadioButton myRbNew;
  private JComboBox myExisitingsCombo;
  private EditChangelistPanel myNewListPanel;
  private final Collection<? extends ChangeList> myExistingLists;
  private Project myProject;
  private LocalChangeList mySelectedList;


  public ChangeListChooser(@NotNull Project project,
                           @NotNull Collection<? extends ChangeList> changelists,
                           @Nullable ChangeList defaultSelection) {
    super(project, false);
    myExistingLists = changelists;
    myProject = project;

    final DefaultComboBoxModel model = new DefaultComboBoxModel();
    for (ChangeList list : changelists) {
      model.addElement(list);
    }

    myExisitingsCombo.setModel(model);
    if (defaultSelection == null) {
      myExisitingsCombo.setSelectedIndex(0);
    }
    else {
      myExisitingsCombo.setSelectedItem(defaultSelection);
    }

    myExisitingsCombo.setRenderer(new ColoredListCellRenderer() {
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        append(((ChangeList)value).getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    });

    myRbExisting.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateEnabledItems();
      }
    });

    if (defaultSelection != null) {
      myRbExisting.setSelected(true);
    }
    else {
      myRbNew.setSelected(true);
    }

    updateEnabledItems();

    setTitle(VcsBundle.message("changes.changelist.chooser.title"));

    init();
  }

  private void updateEnabledItems() {
    if (myRbExisting.isSelected()) {
      myExisitingsCombo.setEnabled(true);
      myNewListPanel.setEnabled(false);
      myExisitingsCombo.requestFocus();
    }
    else {
      myExisitingsCombo.setEnabled(false);
      myNewListPanel.setEnabled(true);
      myNewListPanel.requestFocus();
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myRbExisting.isSelected() ? myExisitingsCombo : myNewListPanel.getPrefferedFocusedComponent();
  }

  protected String getDimensionServiceKey() {
    return "VCS.ChangelistChooser";
  }

  protected void doOKAction() {
    if (myRbNew.isSelected()) {
      String newText = myNewListPanel.getName();
      for (ChangeList list : myExistingLists) {
        if (newText.equals(list.getName())) {
          Messages.showErrorDialog(myProject,
                                   VcsBundle.message("changes.newchangelist.warning.already.exists.text", newText),
                                   VcsBundle.message("changes.newchangelist.warning.already.exists.title"));
          return;
        }
      }
    }

    if (myRbExisting.isSelected()) {
      mySelectedList = (LocalChangeList)myExisitingsCombo.getSelectedItem();
    }
    else {
      mySelectedList = ChangeListManager.getInstance(myProject).addChangeList(myNewListPanel.getName(), myNewListPanel.getDescription());
    }

    super.doOKAction();
  }

  public LocalChangeList getSelectedList() {
    return mySelectedList;
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
