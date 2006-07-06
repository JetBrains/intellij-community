/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.module.ModuleType;
import com.intellij.util.containers.MultiMap;

import javax.swing.*;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 6, 2004
 */
public class ProjectWizardStepFactoryImpl extends ProjectWizardStepFactory implements ApplicationComponent{

  private final MultiMap<ModuleType, AddSupportStepsProvider> myStepsProviders = new MultiMap<ModuleType, AddSupportStepsProvider>();

  public ModuleWizardStep createNameAndLocationStep(WizardContext wizardContext, JavaModuleBuilder builder, ModulesProvider modulesProvider, Icon icon, String helpId) {
    return new NameLocationStep(wizardContext, builder, modulesProvider, icon, helpId);
  }

  /**
   * @deprecated
   */
  public ModuleWizardStep createOutputPathPathsStep(ModuleWizardStep nameAndLocationStep, JavaModuleBuilder builder, Icon icon, String helpId) {
    return new OutputPathsStep((NameLocationStep)nameAndLocationStep, builder, icon, helpId);
  }

  public ModuleWizardStep createSourcePathsStep(ModuleWizardStep nameAndLocationStep, JavaModuleBuilder builder, Icon icon, String helpId) {
    return new SourcePathsStep((NameLocationStep)nameAndLocationStep, builder, icon, helpId);
  }

  public ModuleWizardStep createProjectJdkStep(WizardContext context,
                                               final JavaModuleBuilder builder,
                                               final Computable<Boolean> isVisible,
                                               final Icon icon,
                                               final String helpId) {
    return new ProjectJdkStep(context){
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

  public AddSupportStep createLoadJarsStep(AddSupportContext context, String title, Icon icon) {
    return new LoadJarsStep<AddSupportContext>(context, title, icon);
  }

  public void registerAddSupportProvider(final ModuleType moduleType, AddSupportStepsProvider provider) {
    myStepsProviders.putValue(moduleType, provider);
  }

  @NotNull
  public AddSupportStepsProvider[] getAddSupportProviders(ModuleType moduleType) {
    return myStepsProviders.get(moduleType).toArray(AddSupportStepsProvider.EMPTY_ARRAY);
  }

  public ModuleWizardStep[] createAddSupportSteps(WizardContext wizardContext,
                                                  ModuleBuilder moduleBuilder,
                                                  ModulesProvider modulesProvider,
                                                  final Icon icon) {

    ArrayList<ModuleWizardStep> result = new ArrayList<ModuleWizardStep>();
    ArrayList<AddSupportContext> contexts = new ArrayList<AddSupportContext>();
    final AddSupportStepsProvider[] providers = ProjectWizardStepFactory.getInstance().getAddSupportProviders(moduleBuilder.getModuleType());
    if (providers.length > 0) {
      for (AddSupportStepsProvider provider: providers) {
        final AddSupportStep[] wizardSteps = provider.createAddSupportSteps(wizardContext, moduleBuilder, modulesProvider);
        result.addAll(Arrays.asList(wizardSteps));
        contexts.add(wizardSteps[0].myContext);
      }
      final AddSupportContext[] supportContexts = contexts.toArray(new AddSupportContext[contexts.size()]);
      moduleBuilder.setAddSupportContexts(supportContexts);
      final AddSupportFeaturesStep featuresStep =
        new AddSupportFeaturesStep(providers, supportContexts, icon);
      result.add(0, featuresStep);
    }
    return result.toArray(ModuleWizardStep.EMPTY_ARRAY);
  }

  @NotNull
  public String getComponentName() {
    return "ProjectWizardStepFactory";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }
}
