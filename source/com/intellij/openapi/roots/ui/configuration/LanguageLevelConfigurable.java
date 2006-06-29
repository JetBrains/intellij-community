/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.ui.Messages;
import com.intellij.pom.java.LanguageLevel;

import javax.swing.*;
import java.awt.*;

/**
 * User: anna
 * Date: 06-Jun-2006
 */
public class LanguageLevelConfigurable implements UnnamedConfigurable {
  private ReloadProjectRequest myReloadProjectRequest;

  private LanguageLevelCombo myLanguageLevelCombo;

  private JPanel myPanel = new JPanel(new GridBagLayout());

  private Module myModule;


  public LanguageLevelConfigurable(Module module) {
    myModule = module;
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
    final LanguageLevel moduleLanguageLevel = myModule.getLanguageLevel();
    if (moduleLanguageLevel == null) {
      return myLanguageLevelCombo.getSelectedItem() != LanguageLevelCombo.USE_PROJECT_LANGUAGE_LEVEL;
    }
    return !myLanguageLevelCombo.getSelectedItem().equals(moduleLanguageLevel);
  }

  public void apply() throws ConfigurationException {
    final LanguageLevel newLanguageLevel = myLanguageLevelCombo.getSelectedItem() != LanguageLevelCombo.USE_PROJECT_LANGUAGE_LEVEL ? (LanguageLevel)myLanguageLevelCombo.getSelectedItem() : null;
    ((ModuleRootManagerImpl)ModuleRootManager.getInstance(myModule)).setLanguageLevel(newLanguageLevel);
    myReloadProjectRequest.start();
  }

  public void reset() {
    final LanguageLevel originalLanguageLevel = myModule.getLanguageLevel();
    myLanguageLevelCombo.setSelectedItem(originalLanguageLevel);
    myReloadProjectRequest = new ReloadProjectRequest(originalLanguageLevel != null
                                                      ? originalLanguageLevel
                                                      : ProjectRootManagerEx.getInstanceEx(myModule.getProject()).getLanguageLevel()); //use project language level
  }

  public void disposeUIResources() {
    myPanel = null;
    myLanguageLevelCombo = null;
    myReloadProjectRequest = null;
  }

  private class ReloadProjectRequest implements Runnable {
    private final LanguageLevel myOriginalLanguageLevel;

    public ReloadProjectRequest(final LanguageLevel originalLanguageLevel) {
      myOriginalLanguageLevel = originalLanguageLevel;
    }

    public void start() {
      final ModuleRootManagerImpl manager = (ModuleRootManagerImpl)ModuleRootManager.getInstance(myModule);
      if (!myOriginalLanguageLevel.equals(manager.getLanguageLevel())) {
        ApplicationManager.getApplication().invokeLater(this, ModalityState.NON_MMODAL);
      }
    }

    public void run() {
      final Project project = myModule.getProject();
      final String _message = ProjectBundle.message("module.project.language.level.changed.reload.prompt", project.getName());
      if (Messages.showYesNoDialog(project, _message, ProjectBundle.message("modules.title"), Messages.getQuestionIcon()) == 0) {
        ProjectManagerEx.getInstanceEx().reloadProject(project);
      }
    }
  }
}
