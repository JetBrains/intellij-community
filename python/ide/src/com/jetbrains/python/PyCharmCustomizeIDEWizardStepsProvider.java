// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.ide.customize.*;
import com.intellij.openapi.util.Pair;

import javax.swing.*;
import java.util.List;
import java.util.Map;

public class PyCharmCustomizeIDEWizardStepsProvider implements CustomizeIDEWizardStepsProvider {
  @Override
  public void initSteps(CustomizeIDEWizardDialog wizardDialog, List<AbstractCustomizeWizardStep> steps) {
    PluginGroups groups = new PluginGroups() {
      @Override
      protected void initGroups(Map<String, Pair<Icon, List<String>>> tree, Map<String, String> featuredPlugins) {
        addVimPlugin(featuredPlugins);
        addMarkdownPlugin(featuredPlugins);
        featuredPlugins.put("BashSupport", "Languages:Bash language support:BashSupport");
        featuredPlugins.put("R Language Support", "Languages:R language support:R4Intellij");
      }
    };

    steps.add(new CustomizeUIThemeStepPanel());

    if (CustomizeLauncherScriptStep.isAvailable()) {
      steps.add(new CustomizeLauncherScriptStep());
    }

    steps.add(new CustomizeFeaturedPluginsStepPanel(groups));
  }
}
