/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * User: anna
 * Date: 06-Jun-2006
 */
public class LanguageLevelConfigurable implements UnnamedConfigurable {

  private LanguageLevelCombo myLanguageLevelCombo;

  private JPanel myPanel = new JPanel(new GridBagLayout());

  public LanguageLevelModuleExtension myLanguageLevelExtension;


  public LanguageLevelConfigurable(ModifiableRootModel rootModule) {
    myLanguageLevelExtension = rootModule.getModuleExtension(LanguageLevelModuleExtension.class);
    init();
  }

  public JComponent createComponent() {
    return myPanel;
  }

  private void init() {
    myLanguageLevelCombo = new LanguageLevelCombo();
    myLanguageLevelCombo.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final Object languageLevel = myLanguageLevelCombo.getSelectedItem();
        myLanguageLevelExtension.setLanguageLevel(languageLevel instanceof LanguageLevel ? (LanguageLevel)languageLevel : null);
      }
    });
    myLanguageLevelCombo.insertItemAt(LanguageLevelCombo.USE_PROJECT_LANGUAGE_LEVEL, 0);
    myPanel.add(new JLabel(ProjectBundle.message("module.module.language.level")), new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(8, 6, 6, 0), 0, 0));
    myPanel.add(myLanguageLevelCombo, new GridBagConstraints(1, 0, 1, 1, 1.0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(6, 6, 6, 0), 0, 0));
  }

  public boolean isModified() {
    return myLanguageLevelExtension.isChanged();
  }

  public void apply() throws ConfigurationException {
    myLanguageLevelExtension.commit();
  }

  public void reset() {
    myLanguageLevelCombo.setSelectedItem(myLanguageLevelExtension.getLanguageLevel());
  }

  public void disposeUIResources() {
    myPanel = null;
    myLanguageLevelCombo = null;
    myLanguageLevelExtension = null;
  }

}
