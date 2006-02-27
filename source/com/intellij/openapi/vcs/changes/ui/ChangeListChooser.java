package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
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
  private JTextField myNewListNameField;
  private final Collection<ChangeList> myExistingLists;
  private Project myProject;
  private ChangeList mySelectedList;


  public ChangeListChooser(@NotNull Project project,
                           @NotNull Collection<ChangeList> changelists,
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
        append(((ChangeList)value).getDescription(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    });

    myRbExisting.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateEnabledItems();
      }
    });

    if (myExistingLists.size() > 1) {
      myRbExisting.setSelected(true);
    }
    else {
      myRbNew.setSelected(true);
    }

    updateEnabledItems();

    setTitle("Choose Changelist");

    init();
  }

  private void updateEnabledItems() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (myRbExisting.isSelected()) {
          myExisitingsCombo.setEnabled(true);
          myNewListNameField.setEnabled(false);
          myExisitingsCombo.requestFocus();
        }
        else {
          myExisitingsCombo.setEnabled(false);
          myNewListNameField.setEnabled(true);
          myNewListNameField.requestFocus();
        }
      }
    });
  }

  public JComponent getPreferredFocusedComponent() {
    return myExistingLists.size() > 0 ? myExisitingsCombo : myNewListNameField;
  }

  protected void doOKAction() {
    if (myRbNew.isSelected()) {
      String newText = myNewListNameField.getText();
      for (ChangeList list : myExistingLists) {
        if (newText.equals(list.getDescription())) {
          Messages.showErrorDialog(myProject, "Changelist '" + newText + "' already exists.", "Wrong Changelist Name");
          return;
        }
      }
    }

    if (myRbExisting.isSelected()) {
      mySelectedList = (ChangeList)myExisitingsCombo.getSelectedItem();
    }
    else {
      mySelectedList = ChangeListManager.getInstance(myProject).addChangeList(myNewListNameField.getText());
    }

    super.doOKAction();
  }

  public ChangeList getSelectedList() {
    return mySelectedList;
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
