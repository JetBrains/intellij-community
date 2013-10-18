package com.jetbrains.python.module;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.platform.DirectoryProjectGenerator;

/**
 * @author yole
 */
public class PyModuleServiceImpl extends PyModuleService {
  @Override
  public ModuleBuilder createPythonModuleBuilder(DirectoryProjectGenerator generator) {
    return new PythonModuleBuilderBase(generator);
  }
}
