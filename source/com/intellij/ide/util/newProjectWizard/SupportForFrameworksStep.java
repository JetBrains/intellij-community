/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportUtil;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.List;

/**
 * @author nik
 */
public class SupportForFrameworksStep extends ModuleWizardStep {
  private AddSupportForFrameworksPanel mySupportForFrameworksPanel;

  public SupportForFrameworksStep(final ModuleBuilder builder) {
    List<FrameworkSupportProvider> providers = FrameworkSupportUtil.getProviders(builder.getModuleType());
    mySupportForFrameworksPanel = new AddSupportForFrameworksPanel(providers, new Computable<String>() {
      public String compute() {
        return getBaseDirectory(builder);
      }
    });
    builder.addModuleConfigurationUpdater(new ModuleBuilder.ModuleConfigurationUpdater() {
      public void update(final Module module, final ModifiableRootModel rootModel) {
        mySupportForFrameworksPanel.addSupport(module, rootModel);
      }
    });
  }

  private static String getBaseDirectory(final ModuleBuilder builder) {
    String path = null;
    if (builder instanceof JavaModuleBuilder) {
      path = ((JavaModuleBuilder)builder).getContentEntryPath();
    }
    if (path == null) {
      path = builder.getModuleFileDirectory();
    }
    return path != null ? FileUtil.toSystemIndependentName(path) : "";
  }

  public Icon getIcon() {
    return ICON;
  }

  @NonNls
  public String getHelpId() {
    return "reference.dialogs.new.project.technologies";
  }

  public void _commit(final boolean finishChosen) throws CommitStepException {
    if (finishChosen) {
      mySupportForFrameworksPanel.downloadLibraries();
    }
  }

  public JComponent getComponent() {
    return mySupportForFrameworksPanel.getMainPanel();
  }

  public void updateDataModel() {
  }
}
