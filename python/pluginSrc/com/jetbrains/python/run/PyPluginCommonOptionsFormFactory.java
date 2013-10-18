package com.jetbrains.python.run;

/**
 * @author yole
 */
public class PyPluginCommonOptionsFormFactory extends PyCommonOptionsFormFactory {
  @Override
  public AbstractPyCommonOptionsForm createForm(PyCommonOptionsFormData data) {
    return new PyPluginCommonOptionsForm(data);
  }
}
