/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.util.scopeChooser;

import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.ui.ComboboxWithBrowseButton;
import org.jetbrains.annotations.Nullable;

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
        final NamedScope scope = (NamedScope)combo.getSelectedItem();
        final ScopeChooserConfigurable configurable = ScopeChooserConfigurable.getInstance(myProject);
        final EditScopesDialog dlg = EditScopesDialog.editConfigurable(myProject, new Runnable() {
          public void run() {
            configurable.selectNodeInTree(scope.getName());
          }
        }, true);
        if (dlg.isOK()){
          rebuild();
          final NamedScope namedScope = dlg.getSelectedScope();
          if (namedScope != null) {
            selectScope(namedScope.getName());
          }
        }
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
    final DefaultComboBoxModel model = new DefaultComboBoxModel(manager.getScopes());
    model.removeElement(manager.getProblemsScope());
    return model;
  }

  @Nullable
  public NamedScope getSelectedScope() {
    JComboBox combo = getComboBox();
    int idx = combo.getSelectedIndex();
    if (idx < 0) return null;
    return (NamedScope)combo.getSelectedItem();
  }
}