package com.jetbrains.python.run;

/**
 * @author yole
 */
public class PyCommonOptionsFormFactory {
  private static PyCommonOptionsFormFactory ourInstance = new PyCommonOptionsFormFactory();

  private PyCommonOptionsFormFactory() {
  }

  public static PyCommonOptionsFormFactory getInstance() {
    return ourInstance;
  }

  public AbstractPyCommonOptionsForm createForm(AbstractPythonRunConfiguration runConfiguration) {
    return new PyCommonOptionsForm(runConfiguration);
  }
}
