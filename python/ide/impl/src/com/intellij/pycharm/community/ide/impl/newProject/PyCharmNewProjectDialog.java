// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.newProject;

import com.intellij.ide.util.projectWizard.AbstractNewProjectDialog;
import com.intellij.pycharm.community.ide.impl.newProject.steps.PyCharmNewProjectStep;

public class PyCharmNewProjectDialog extends AbstractNewProjectDialog {
  @Override
  protected PyCharmNewProjectStep createRootStep() {
    return new PyCharmNewProjectStep();
  }

  @Override
  protected String getHelpId() {
    return "concepts.project";
  }
}
