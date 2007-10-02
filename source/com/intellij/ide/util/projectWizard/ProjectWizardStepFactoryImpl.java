/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.util.newProjectWizard.SourcePathsStep;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 6, 2004
 */
public class ProjectWizardStepFactoryImpl extends ProjectWizardStepFactory {

  public ModuleWizardStep createNameAndLocationStep(WizardContext wizardContext, JavaModuleBuilder builder, ModulesProvider modulesProvider, Icon icon, String helpId) {
    return new NameLocationStep(wizardContext, builder, modulesProvider, icon, helpId);
  }

  public ModuleWizardStep createNameAndLocationStep(final WizardContext wizardContext) {
    return new ProjectNameStep(wizardContext);
  }

  /**
   * @deprecated
   */
  public ModuleWizardStep createOutputPathPathsStep(ModuleWizardStep nameAndLocationStep, JavaModuleBuilder builder, Icon icon, String helpId) {
    return new OutputPathsStep((NameLocationStep)nameAndLocationStep, builder, icon, helpId);
  }

  public ModuleWizardStep createSourcePathsStep(ModuleWizardStep nameAndLocationStep, SourcePathsBuilder builder, Icon icon, String helpId) {
    return null;
  }

  public ModuleWizardStep createSourcePathsStep(final WizardContext context, final SourcePathsBuilder builder, final Icon icon, @NonNls final String helpId) {
    return new SourcePathsStep(builder, icon, helpId);
  }

  public ModuleWizardStep createProjectJdkStep(WizardContext context,
                                               final JavaModuleBuilder builder,
                                               final Computable<Boolean> isVisible,
                                               final Icon icon,
                                               final String helpId) {
    return createProjectJdkStep(context, null, builder, isVisible, icon, helpId);
  }

  public ModuleWizardStep createProjectJdkStep(WizardContext context,
                                               SdkType type,
                                               final JavaModuleBuilder builder,
                                               final Computable<Boolean> isVisible,
                                               final Icon icon,
                                               @NonNls final String helpId) {
    return new ProjectJdkForModuleStep(context, type){
      public void updateDataModel() {
        super.updateDataModel();
        builder.setModuleJdk(getJdk());
      }

      public boolean isStepVisible() {
        return isVisible.compute().booleanValue();
      }

      public Icon getIcon() {
        return icon;
      }

      public String getHelpId() {
        return helpId;
      }
    };
  }

  public ModuleWizardStep createProjectJdkStep(final WizardContext wizardContext) {
    return new ProjectJdkStep(wizardContext){
      public boolean isStepVisible() {
        return com.intellij.ide.util.newProjectWizard.AddModuleWizard.getNewProjectJdk(wizardContext) == null;
      }
    };
  }

}
