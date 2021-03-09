// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.ide.customize.*;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PyCharmCustomizeIDEWizardStepsProvider implements CustomizeIDEWizardStepsProvider {
  @Override
  public void initSteps(CustomizeIDEWizardDialog wizardDialog, @NotNull List<? super AbstractCustomizeWizardStep> steps) {
    PluginGroups groups = new PluginGroups() {

      @Override
      protected @NotNull List<? extends PluginGroupDescription> getInitialFeaturedPlugins() {
        return List.of(
          PluginGroupDescription.vim(),
          PluginGroupDescription.r(),
          PluginGroupDescription.aws(),
          PluginGroupDescription.bigDataTools()
        );
      }
    };

    if (SystemInfo.isMac) {
      steps.add(new CustomizeMacKeyboardLayoutStep());
    }

    steps.add(new CustomizeUIThemeStepPanel());

    if (CustomizeLauncherScriptStep.isAvailable()) {
      steps.add(new CustomizeLauncherScriptStep());
    }

    steps.add(new CustomizeFeaturedPluginsStepPanel(groups));
  }
}
