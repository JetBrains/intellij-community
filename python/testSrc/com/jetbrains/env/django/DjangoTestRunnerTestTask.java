package com.jetbrains.env.django;

import com.jetbrains.python.fixtures.PyProfessionalTestCase;

/**
 * User : ktisha
 */
public abstract class DjangoTestRunnerTestTask extends DjangoManageTestTask {

  @Override
  protected String getTestDataPath() {
    return PyProfessionalTestCase.getProfessionalTestDataPath() + "/django/testRunner/" + getTestName();
  }

  abstract String getTestName();

  @Override
  protected String getSubcommand() {
    return "validate";
  }
}
