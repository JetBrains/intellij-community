/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.util.scopeChooser;

import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.ui.ComboboxWithBrowseButton;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class PackageSetChooserCombo extends ComboboxWithBrowseButton {
  private Project myProject;

  public PackageSetChooserCombo(final Project project, String preselect) {
    final JComboBox combo = getComboBox();
    myProject = project;
    addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        NamedScope scope = (NamedScope)combo.getSelectedItem();
        ScopeChooserDialog dlg = new ScopeChooserDialog(myProject, DependencyValidationManager.getInstance(project));
        if (scope != null) {
          dlg.setSelectedScope(scope.getName());
        }
        dlg.show();
        rebuild();
        selectScope(dlg.getSelectedScope());
      }
    });

    combo.setRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        setText(value == null ? "" : ((NamedScope)value).getName());
        return this;
      }
    });

    rebuild();

    selectScope(preselect);
  }

  private void selectScope(String preselect) {
    final JComboBox combo = getComboBox();
    if (preselect != null) {
      DefaultComboBoxModel model = (DefaultComboBoxModel)combo.getModel();
      for (int i = 0; i < model.getSize(); i++) {
        NamedScope descriptor = (NamedScope)model.getElementAt(i);
        if (preselect.equals(descriptor.getName())) {
          combo.setSelectedIndex(i);
          break;
        }
      }
    }
  }

  private void rebuild() {
    getComboBox().setModel(createModel());
  }

  private DefaultComboBoxModel createModel() {
    DependencyValidationManager manager = DependencyValidationManager.getInstance(myProject);
    return new DefaultComboBoxModel(manager.getScopes());
  }

  public NamedScope getSelectedScope() {
    JComboBox combo = getComboBox();
    int idx = combo.getSelectedIndex();
    if (idx < 0) return null;
    return (NamedScope)combo.getSelectedItem();
  }
}