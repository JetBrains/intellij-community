// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.module;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.platform.DirectoryProjectGenerator;

public abstract class PyModuleServiceEx extends PyModuleService {
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
