/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 20.12.2006
 * Time: 19:39:53
 */
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.changes.IgnoredFileBean;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.help.HelpManager;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class IgnoredSettingsDialog extends DialogWrapper {
  private JList myList;
  private JButton myAddButton;
  private JButton myEditButton;
  private JButton myRemoveButton;
  private JPanel myPanel;
  private final Project myProject;
  private DefaultListModel myModel;

  public IgnoredSettingsDialog(Project project) {
    super(project, true);
    myProject = project;
    setTitle(VcsBundle.message("ignored.configure.title"));
    
    init();
    myList.setCellRenderer(new MyCellRenderer());
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        addItem();
      }
    });
    myEditButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        editItem();
      }
    });
    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        deleteItems();
      }
    });
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("configureIgnoredFilesDialog");
  }

  private void setItems(final IgnoredFileBean[] filesToIgnore) {
    myModel = new DefaultListModel();
    for(IgnoredFileBean bean: filesToIgnore) {
      myModel.addElement(bean);
    }
    myList.setModel(myModel);
  }

  private IgnoredFileBean[] getItems() {
    final int count = myList.getModel().getSize();
    IgnoredFileBean[] result = new IgnoredFileBean[count];
    for(int i=0; i<count; i++) {
      result [i] = (IgnoredFileBean) myList.getModel().getElementAt(i);
    }
    return result;
  }

  private void addItem() {
    IgnoreUnversionedDialog dlg = new IgnoreUnversionedDialog(myProject);
    dlg.show();
    if (dlg.isOK()) {
      final IgnoredFileBean[] ignoredFiles = dlg.getSelectedIgnoredFiles();
      for(IgnoredFileBean bean: ignoredFiles) {
        myModel.addElement(bean);
      }
    }
  }

  private void editItem() {
    IgnoredFileBean bean = (IgnoredFileBean) myList.getSelectedValue();
    if (bean == null) return;
    IgnoreUnversionedDialog dlg = new IgnoreUnversionedDialog(myProject);
    dlg.setIgnoredFile(bean);
    dlg.show();
    if (dlg.isOK()) {
      IgnoredFileBean[] beans = dlg.getSelectedIgnoredFiles();
      assert beans.length == 1;
      int selectedIndex = myList.getSelectedIndex();
      myModel.setElementAt(beans [0], selectedIndex);
    }
  }

  private void deleteItems() {
    boolean contigiousSelection = true;    
    int minSelectionIndex = myList.getSelectionModel().getMinSelectionIndex();
    int maxSelectionIndex = myList.getSelectionModel().getMaxSelectionIndex();
    for(int i=minSelectionIndex; i<=maxSelectionIndex; i++) {
      if (!myList.getSelectionModel().isSelectedIndex(i)) {
        contigiousSelection = false;
        break;
      }
    }
    if (contigiousSelection) {
      myModel.removeRange(minSelectionIndex, maxSelectionIndex);
    }
    else {
      final Object[] selection = myList.getSelectedValues();
      for(Object item: selection) {
        myModel.removeElement(item);
      }
    }
  }

  public static void configure(final Project project) {
    IgnoredSettingsDialog dlg = new IgnoredSettingsDialog(project);
    dlg.setItems(ChangeListManager.getInstance(project).getFilesToIgnore());
    dlg.show();
    if (!dlg.isOK()) {
      return;
    }
    ChangeListManager.getInstance(project).setFilesToIgnore(dlg.getItems());
    dlg.dispose();
  }

  @Override @NonNls
  protected String getDimensionServiceKey() {
    return "IgnoredSettingsDialog";
  }

  private static class MyCellRenderer extends ColoredListCellRenderer {
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      IgnoredFileBean bean = (IgnoredFileBean) value;
      final String path = bean.getPath();
      if (path != null) {
        if (path.endsWith("/")) {
          append(VcsBundle.message("ignored.configure.item.directory", path), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
        else {
          append(VcsBundle.message("ignored.configure.item.file", path), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }
      else if (bean.getMask() != null) {
        append(VcsBundle.message("ignored.configure.item.mask", bean.getMask()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }
  }

}