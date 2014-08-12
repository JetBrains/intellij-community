package com.jetbrains.env.django;

import com.jetbrains.python.fixtures.PyProfessionalTestCase;

/**
 * User : catherine
 */
public abstract class DjangoPathTestTask extends DjangoManageTestTask {

  @Override
  protected String getTestDataPath() {
    return PyProfessionalTestCase.getProfessionalTestDataPath() + "/django/path/djangoPath";
  }

  @Override
  protected String getSubcommand() {
    return "validate";
  }
}
