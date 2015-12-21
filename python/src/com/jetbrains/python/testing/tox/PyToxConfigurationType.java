/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.testing.tox;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Ilya.Kazakevich
 */
public class PyToxConfigurationType implements ConfigurationType {

  public static final String ID = "Tox";
  public static final ConfigurationType INSTANCE = new PyToxConfigurationType();

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{new PyToxConfigurationFactory(this)};
  }

  @Override
  public String getDisplayName() {
    return ID;
  }

  @Override
  public String getConfigurationTypeDescription() {
    return "Tox runner";
  }

  @Override
  public Icon getIcon() {
    return PythonIcons.Python.PythonTests;
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }
}
