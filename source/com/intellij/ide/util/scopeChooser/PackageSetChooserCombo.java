/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.util.scopeChooser;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.ui.ComboboxWithBrowseButton;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;

public class PackageSetChooserCombo extends ComboboxWithBrowseButton {
  private Project myProject;
  private static final Logger LOG = Logger.getInstance("#" + PackageSetChooserCombo.class.getName());

  public PackageSetChooserCombo(final Project project, String preselect) {
    final JComboBox combo = getComboBox();
    combo.setBorder(null);
    myProject = project;
    addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final NamedScope scope = (NamedScope)combo.getSelectedItem();
        if (scope instanceof NamedScope.UnnamedScope) {
          final Map<String,PackageSet> unnamedScoopes = DependencyValidationManager.getInstance(myProject).getUnnamedScopes();
          final EditUnnamedScopesDialog dlg = new EditUnnamedScopesDialog(scope);
          dlg.show();
          if (dlg.isOK()) {
            final PackageSet packageSet = scope.getValue();
            LOG.assertTrue(packageSet != null);
            unnamedScoopes.remove(packageSet.getText());
            final PackageSet editedScope = dlg.getScope();
            if (editedScope != null) {
              unnamedScoopes.put(editedScope.getText(), editedScope);
            }
            rebuild();
            if (editedScope != null) {
              selectScope(editedScope.getText());
            }
          }
        } else {
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
    final Map<String, PackageSet> unnamedScopes = manager.getUnnamedScopes();
    for (PackageSet unnamedScope : unnamedScopes.values()) {
      model.addElement(new NamedScope.UnnamedScope(unnamedScope));
    }
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

  private class EditUnnamedScopesDialog extends DialogWrapper {
    private PackageSet myScope;
    private ScopeEditorPanel myPanel;

    public EditUnnamedScopesDialog(final NamedScope scope) {
      super(PackageSetChooserCombo.this, false);
      myScope = scope.getValue();
      myPanel = new ScopeEditorPanel(myProject, DependencyValidationManager.getInstance(myProject));
      init();
      myPanel.reset(myScope, null);
    }

    @Nullable
    protected JComponent createCenterPanel() {
      return myPanel.getPanel();
    }

    protected void doOKAction() {
      myScope = myPanel.getCurrentScope();
      super.doOKAction();
    }

    public PackageSet getScope() {
      return myScope;
    }
  }
}