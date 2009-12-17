package com.jetbrains.python;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.module.ModuleType;
import org.jetbrains.annotations.NonNls;

/**
 * @author yole
 */
public abstract class PythonModuleTypeBase<T extends ModuleBuilder> extends ModuleType<T> {
  protected PythonModuleTypeBase(@NonNls String id) {
    super(id);
  }
}
