package com.jetbrains.python.testing.pytest;

/**
 * User : catherine
 */
public interface PyTestRunConfigurationParams {
  public boolean useParam();
  public void useParam(boolean useParam);
  public boolean useKeyword();
  public void useKeyword(boolean useKeyword);
}
