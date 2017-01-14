/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.testing;

import com.intellij.execution.configurations.ConfigurationType;
import com.jetbrains.python.PyBundle;
import icons.PythonIcons;

import javax.swing.*;

/**
 * @author Ilya.Kazakevich
 */
public abstract class PythonTestConfigurationType implements ConfigurationType {
  public String getDisplayName() {
    return PyBundle.message("runcfg.test.display_name");
  }

  public String getConfigurationTypeDescription() {
    return PyBundle.message("runcfg.test.description");
  }

  public Icon getIcon() {
    return PythonIcons.Python.PythonTests;
  }
}
