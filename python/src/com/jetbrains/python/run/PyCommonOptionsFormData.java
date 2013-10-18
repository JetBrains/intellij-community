package com.jetbrains.python.run;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

import java.util.List;

/**
 * @author traff
 */
public interface PyCommonOptionsFormData {
  Project getProject();
  List<Module> getValidModules();
  boolean showConfigureInterpretersLink();
}
