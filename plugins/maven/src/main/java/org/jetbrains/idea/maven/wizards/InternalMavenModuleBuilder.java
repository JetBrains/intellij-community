// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.NewProjectWizardLegacy;
import com.intellij.ide.projectWizard.ProjectSettingsStep;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.importing.MavenProjectImporter;

import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public final class InternalMavenModuleBuilder extends AbstractMavenModuleBuilder {
  @Override
  public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider) {
    return new ModuleWizardStep[]{
      new MavenStructureWizardStep(this, wizardContext),
      new SelectPropertiesStep(wizardContext.getProject(), this)
    };
  }

  @Override
  protected void setupModule(Module module) throws ConfigurationException {
    super.setupModule(module);
    if (MavenProjectImporter.isImportToWorkspaceModelEnabled() || MavenProjectImporter.isImportToTreeStructureEnabled()) {
      //this is needed to ensure that dummy module created here will be correctly replaced by real ModuleEntity when import finishes
      ExternalSystemModulePropertyManager.getInstance(module).setMavenized(true);
    }
  }

  @Override
  public boolean isAvailable() {
    return NewProjectWizardLegacy.isAvailable();
  }

  @NotNull
  @Override
  public List<Class<? extends ModuleWizardStep>> getIgnoredSteps() {
    return Collections.singletonList(ProjectSettingsStep.class);
  }
}
