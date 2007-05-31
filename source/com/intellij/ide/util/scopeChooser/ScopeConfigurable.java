/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.util.scopeChooser;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.packageDependencies.DependencyValidationManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * User: anna
 * Date: 01-Jul-2006
 */
public class ScopeConfigurable extends NamedConfigurable<NamedScope> {
  private NamedScope myScope;
  private ScopeEditorPanel myPanel;
  private String myPackageSet;
  private JCheckBox mySharedCheckbox = new JCheckBox(IdeBundle.message("share.scope.checkbox.title"));
  private boolean myShareScope = false;
  private Project myProject;
  private Icon myIcon;

  public ScopeConfigurable(final NamedScope scope, final boolean shareScope, final Project project, final Runnable updateTree) {
    super(true, updateTree);
    myScope = scope;
    myShareScope = shareScope;
    myProject = project;
    final PackageSet packageSet = scope.getValue();
    myPackageSet = packageSet != null ? packageSet.getText() : null;
    myPanel = new ScopeEditorPanel(project){
      public NamedScopesHolder getHolder() {
        return ScopeConfigurable.this.getHolder();
      }
    };
    myIcon = getHolder(myShareScope).getIcon();
    mySharedCheckbox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myIcon = getHolder().getIcon();
      }
    });
  }

  public void setDisplayName(final String name) {
    if (Comparing.strEqual(myScope.getName(), name)){
      return;
    }
    final PackageSet packageSet = myScope.getValue();
    myScope = new NamedScope(name, packageSet != null ? packageSet.createCopy() : null);
  }

  public NamedScope getEditableObject() {
    return new NamedScope(myScope.getName(), myPanel.getCurrentScope());
  }

  public String getBannerSlogan() {
    return IdeBundle.message("scope.banner.text", myScope.getName());
  }

  public String getDisplayName() {
    return myScope.getName();
  }

  public Icon getIcon() {
    return myIcon;
  }

  public NamedScopesHolder getHolder() {
    return getHolder(mySharedCheckbox.isSelected());
  }

  private NamedScopesHolder getHolder(boolean local) {
    return (NamedScopesHolder)(local
            ? DependencyValidationManager.getInstance(myProject)
            : NamedScopeManager.getInstance(myProject));
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "project.scopes";
  }

  public JComponent createOptionsPanel() {
    final JPanel wholePanel = new JPanel(new BorderLayout());
    wholePanel.add(myPanel.getPanel(), BorderLayout.CENTER);
    wholePanel.add(mySharedCheckbox, BorderLayout.SOUTH);
    return wholePanel;
  }

  public boolean isModified() {
    if (mySharedCheckbox.isSelected() != myShareScope) return true;
    final PackageSet currentScope = myPanel.getCurrentScope();
    return !Comparing.strEqual(myPackageSet, currentScope != null ? currentScope.getText() : null);
  }

  public void apply() throws ConfigurationException {
    try {
      myPanel.apply();
      final PackageSet packageSet = myPanel.getCurrentScope();
      myScope = new NamedScope(myScope.getName(), packageSet);
      myPackageSet = packageSet != null ? packageSet.getText() : null;
      myShareScope = mySharedCheckbox.isSelected();
    }
    catch (ConfigurationException e) {
      //was canceled - didn't change anything
    }
  }

  public void reset() {
    mySharedCheckbox.setSelected(myShareScope);
    myPanel.reset(myScope.getValue(), null);
  }

  public void disposeUIResources() {
    if (myPanel != null){
      myPanel.cancelCurrentProgress();
      myPanel.clearCaches();
      myPanel = null;
    }
  }

  public void cancelCurrentProgress(){
    if (myPanel != null) { //not disposed
      myPanel.cancelCurrentProgress();
    }
  }

  public PackageSet getScope() {
    return myPanel.getCurrentScope();
  }

  public void restoreCanceledProgress() {
    if (myPanel != null) {
      myPanel.restoreCanceledProgress();
    }
  }
}
