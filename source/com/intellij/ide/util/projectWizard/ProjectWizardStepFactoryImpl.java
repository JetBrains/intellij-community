/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 6, 2004
 */
public class ProjectWizardStepFactoryImpl extends ProjectWizardStepFactory implements ApplicationComponent{

  public ModuleWizardStep createNameAndLocationStep(WizardContext wizardContext, JavaModuleBuilder builder, ModulesProvider modulesProvider, Icon icon, String helpId) {
    return new NameLocationStep(wizardContext, builder, modulesProvider, icon, helpId);
  }

  public ModuleWizardStep createOutputPathPathsStep(ModuleWizardStep nameAndLocationStep, JavaModuleBuilder builder, Icon icon, String helpId) {
    return new OutputPathsStep((NameLocationStep)nameAndLocationStep, builder, icon, helpId);
  }

  public ModuleWizardStep createSourcePathsStep(ModuleWizardStep nameAndLocationStep, JavaModuleBuilder builder, Icon icon, String helpId) {
    return new SourcePathsStep((NameLocationStep)nameAndLocationStep, builder, icon, helpId);
  }


  public String getComponentName() {
    return "ProjectWizardStepFactory";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }
}
