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
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: anna
 * Date: 01-Jul-2006
 */
public class ScopeConfigurable extends NamedConfigurable<NamedScope> {
  private Icon myIcon;
  private NamedScope myScope;
  private ScopeEditorPanel myPanel;
  private String myPackageSet;
  
  public ScopeConfigurable(final NamedScope scope,
                           final Project project,
                           final NamedScopesHolder holder,
                           final Icon icon,
                           final Runnable updateTree) {
    super(true, updateTree);
    myScope = scope;
    final PackageSet packageSet = scope.getValue();
    myPackageSet = packageSet != null ? packageSet.getText() : null;
    myPanel = new ScopeEditorPanel(project, holder);
    myIcon = icon;
  }

  public void setDisplayName(final String name) {
    if (Comparing.strEqual(myScope.getName(), name)){
      return;
    }
    final PackageSet packageSet = myScope.getValue();
    myScope = new NamedScope(name, packageSet != null ? packageSet.createCopy() : null);
  }

  public NamedScope getEditableObject() {
    return myScope;
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

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "project.scopes";
  }

  public JComponent createOptionsPanel() {
    return myPanel.getPanel();
  }

  public boolean isModified() {
    final PackageSet currentScope = myPanel.getCurrentScope();
    return !Comparing.strEqual(myPackageSet, currentScope != null ? currentScope.getText() : null);
  }

  public void apply() throws ConfigurationException {
    try {
      myPanel.apply();
      final PackageSet packageSet = myPanel.getCurrentScope();
      myScope = new NamedScope(myScope.getName(), packageSet);
      myPackageSet = packageSet != null ? packageSet.getText() : null;
    }
    catch (ConfigurationException e) {
      //was canceled - didn't change anything
    }
  }

  public void reset() {
    myPanel.reset(myScope.getValue(), null);
  }

  public void disposeUIResources() {
    if (myPanel != null){
      myPanel.cancelCurrentProgress();
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
}
