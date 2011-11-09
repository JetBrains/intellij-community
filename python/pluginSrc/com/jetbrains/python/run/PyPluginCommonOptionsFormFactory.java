package com.jetbrains.python.run;

import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public class PyPluginCommonOptionsFormFactory extends PyCommonOptionsFormFactory {
  @Override
  public AbstractPyCommonOptionsForm createForm(PyCommonOptionsFormData data) {
    return new PyPluginCommonOptionsForm(data);
  }
}
