package com.jetbrains.python.run;

/**
 * @author yole
 */
public class PyPluginCommonOptionsFormFactory extends PyCommonOptionsFormFactory {
  @Override
  public AbstractPyCommonOptionsForm createForm(AbstractPythonRunConfiguration runConfiguration) {
    return new PyPluginCommonOptionsForm(runConfiguration);
  }
}
