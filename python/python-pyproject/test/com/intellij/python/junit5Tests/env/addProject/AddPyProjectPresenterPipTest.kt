package com.intellij.python.junit5Tests.env.addProject

import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase

@PyEnvTestCase
internal class AddPyProjectPresenterPipTest : AddPyProjectPresenterTestBase(
  toolName = "pip",
  additionalChecks = null
)
