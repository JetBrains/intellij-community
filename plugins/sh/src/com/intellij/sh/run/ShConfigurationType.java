// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.run;

import com.intellij.execution.configurations.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.sh.ShLanguage;
import com.intellij.util.EnvironmentUtil;
import icons.SHIcons;
import org.jetbrains.annotations.NotNull;

public class ShConfigurationType extends SimpleConfigurationType {
  public ShConfigurationType() {
    super("ShConfigurationType", ShLanguage.INSTANCE.getID(), ShLanguage.INSTANCE.getID() + " configuration",
          NotNullLazyValue.createValue(() -> SHIcons.ShFile));
  }

  @NotNull
  @Override
  public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
    ShRunConfiguration configuration = new ShRunConfiguration(project, this, ShLanguage.INSTANCE.getID());
    String defaultShell = EnvironmentUtil.getValue("SHELL");
    if (defaultShell != null) {
      configuration.setInterpreterPath(defaultShell);
    }
    return configuration;
  }
}