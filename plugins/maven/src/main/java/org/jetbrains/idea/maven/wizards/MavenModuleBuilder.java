// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Override {@link AbstractMavenModuleBuilder} instead
 */
@Deprecated
public class MavenModuleBuilder extends AbstractMavenModuleBuilder {
  @Override
  public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider) {
    return new ModuleWizardStep[]{
      new MavenModuleWizardStep(this, wizardContext, !wizardContext.isNewWizard()),
      new SelectPropertiesStep(wizardContext.getProject(), this)
    };
  }
}
