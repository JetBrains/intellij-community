/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.run;

import com.intellij.execution.configuration.RunConfigurationExtensionsManager;
import com.intellij.openapi.components.ServiceManager;

/**
 * @author traff
 */
public class PythonRunConfigurationExtensionsManager
  extends RunConfigurationExtensionsManager<AbstractPythonRunConfiguration, PythonRunConfigurationExtension> {

  public PythonRunConfigurationExtensionsManager() {
    super(PythonRunConfigurationExtension.EP_NAME);
  }

  public static PythonRunConfigurationExtensionsManager getInstance() {
    return ServiceManager.getService(PythonRunConfigurationExtensionsManager.class);
  }
}
