package com.jetbrains.env.django;

import com.jetbrains.python.PythonTestUtil;

/**
 * User : ktisha
 */
public abstract class DjangoTestRunnerTestTask extends DjangoManageTestTask {

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/django/testRunner/" + getTestName();
  }

  abstract String getTestName();

  @Override
  protected String getSubcommand() {
    return "validate";
  }
}
