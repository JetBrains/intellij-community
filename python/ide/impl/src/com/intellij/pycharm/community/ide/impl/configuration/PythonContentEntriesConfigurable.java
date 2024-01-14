// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration;

import com.intellij.application.options.ModuleAwareProjectConfigurable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.NonDefaultProjectConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.PlatformContentEntriesConfigurable;
import com.intellij.util.PlatformUtils;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;


public class PythonContentEntriesConfigurable extends ModuleAwareProjectConfigurable implements NonDefaultProjectConfigurable {
  public PythonContentEntriesConfigurable(final Project project) {
    super(project, PyBundle.message("configurable.PythonContentEntriesConfigurable.display.name"), "reference.settingsdialog.project.structure");
  }

  @NotNull
  @Override
  protected Configurable createModuleConfigurable(Module module) {
    if (PlatformUtils.isPyCharmCommunity())
      return new PlatformContentEntriesConfigurable(module, JavaSourceRootType.SOURCE);
    return new PyContentEntriesModuleConfigurable(module);
  }
}
