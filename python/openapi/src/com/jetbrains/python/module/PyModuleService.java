/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.module;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.platform.DirectoryProjectGenerator;

/**
 * @author yole
 */
public abstract class PyModuleService {
  public static PyModuleService getInstance() {
    return ServiceManager.getService(PyModuleService.class);
  }

  /**
   * Creates a ModuleBuilder that creates a Python module and runs the specified DirectoryProjectGenerator to perform
   * further initialization. The showGenerationSettings() method on the generator is not called, and the generateProject() method
   * receives null as the 'settings' parameter.
   *
   * @param generator the generator to run for configuring the project
   * @return the created module builder instance
   */
  public abstract ModuleBuilder createPythonModuleBuilder(DirectoryProjectGenerator generator);
}
