/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl;
import com.intellij.pom.java.LanguageLevel;

import javax.swing.*;
import java.awt.*;

/**
 * User: anna
 * Date: 06-Jun-2006
 */
public class LanguageLevelConfigurable implements UnnamedConfigurable {

  private LanguageLevelCombo myLanguageLevelCombo;

  private JPanel myPanel = new JPanel(new GridBagLayout());

  private ModifiableRootModel myRootModule;


  public LanguageLevelConfigurable(ModifiableRootModel rootModule) {
    myRootModule = rootModule;
    init();
  }

  public JComponent createComponent() {
    return myPanel;
  }

  private void init() {
    myLanguageLevelCombo = new LanguageLevelCombo();
    myLanguageLevelCombo.insertItemAt(LanguageLevelCombo.USE_PROJECT_LANGUAGE_LEVEL, 0);
    myPanel.add(new JLabel(ProjectBundle.message("module.module.language.level")), new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(8, 6, 6, 0), 0, 0));
    myPanel.add(myLanguageLevelCombo, new GridBagConstraints(1, 0, 1, 1, 1.0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(6, 6, 6, 0), 0, 0));
  }

  public boolean isModified() {
    if (myLanguageLevelCombo == null) return false;
    final LanguageLevel moduleLanguageLevel = myRootModule.getLanguageLevel();
    if (moduleLanguageLevel == null) {
      return myLanguageLevelCombo.getSelectedItem() != LanguageLevelCombo.USE_PROJECT_LANGUAGE_LEVEL;
    }
    return !myLanguageLevelCombo.getSelectedItem().equals(moduleLanguageLevel);
  }

  public void apply() throws ConfigurationException {
    final LanguageLevel newLanguageLevel = myLanguageLevelCombo.getSelectedItem() != LanguageLevelCombo.USE_PROJECT_LANGUAGE_LEVEL ?
      (LanguageLevel)myLanguageLevelCombo.getSelectedItem() : null;
    myRootModule.setLanguageLevel(newLanguageLevel);
    ((ProjectRootManagerImpl)ProjectRootManager.getInstance(myRootModule.getModule().getProject())).reloadProjectOnLanguageLevelChange(newLanguageLevel, true);
  }

  public void reset() {
    final LanguageLevel originalLanguageLevel = myRootModule.getLanguageLevel();
    myLanguageLevelCombo.setSelectedItem(originalLanguageLevel);
  }

  public void disposeUIResources() {
    myPanel = null;
    myLanguageLevelCombo = null;
    myRootModule = null;
  }

}
