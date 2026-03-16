// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.tox;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.icons.PythonIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/**
 * @author Ilya.Kazakevich
 */
public final class PyToxConfigurationType implements ConfigurationType {

  public static final String ID = "Tox";
  public static final ConfigurationType INSTANCE = new PyToxConfigurationType();

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{new PyToxConfigurationFactory(this)};
  }

  @Override
  public String getHelpTopic() {
    return "reference.dialogs.rundebug.Tox";
  }

  @Override
  public @NotNull String getDisplayName() {
    return PyBundle.message("runcfg.tox");
  }

  @Override
  public String getConfigurationTypeDescription() {
    return PyBundle.message("runcfg.tox.runner");
  }

  @Override
  public Icon getIcon() {
    return PythonIcons.Python.PythonTests;
  }

  @Override
  public @NotNull String getId() {
    return ID;
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}
