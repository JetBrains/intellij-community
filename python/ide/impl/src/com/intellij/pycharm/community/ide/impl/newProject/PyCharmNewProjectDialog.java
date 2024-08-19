// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.newProject;

import com.intellij.ide.util.projectWizard.AbstractNewProjectDialog;
import com.intellij.ide.util.projectWizard.AbstractNewProjectStep;
import com.intellij.pycharm.community.ide.impl.newProject.steps.PyCharmNewProjectStep;
import org.jetbrains.annotations.NotNull;

public class PyCharmNewProjectDialog extends AbstractNewProjectDialog {
  @Override
  protected @NotNull AbstractNewProjectStep<?> createNewProjectStep() {
    return new PyCharmNewProjectStep();
  }

  @Override
  protected String getHelpId() {
    return "concepts.project";
  }
}
