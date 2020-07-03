// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.ide.customize.*;
import com.intellij.openapi.util.SystemInfo;
import com.jetbrains.python.conda.PyCharmCustomizeCondaSetupStep;
import com.jetbrains.python.conda.PythonMinicondaLocator;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class PyCharmCustomizeIDEWizardStepsProvider implements CustomizeIDEWizardStepsProvider {
  @Override
  public void initSteps(CustomizeIDEWizardDialog wizardDialog, List<AbstractCustomizeWizardStep> steps) {
    PluginGroups groups = new PluginGroups() {
      @Override
      protected void initGroups(@NotNull List<Group> tree, @NotNull Map<String, String> featuredPlugins) {
        addVimPlugin(featuredPlugins);
        addRPlugin(featuredPlugins);
        addAwsPlugin(featuredPlugins);
        addBigDataToolsPlugin(featuredPlugins);
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

    if (PythonMinicondaLocator.isInstallerExists()) {
      steps.add(new PyCharmCustomizeCondaSetupStep());
    }
  }
}
