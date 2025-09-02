// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.venv.showCase

import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.python.community.impl.venv.tests.pyVenvFixture
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pySdkFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@PyEnvTestCase
class PyEnvWithVenvShowCaseTest {
  private val tempPathFixture = tempPathFixture()
  private val moduleFixture = projectFixture().moduleFixture(tempPathFixture, addPathToSourceRoot = true)
  private val venvFixture = pySdkFixture().pyVenvFixture( // <-- venv fixture
    where = tempPathFixture,
    addToSdkTable = true,
    moduleFixture = moduleFixture
  )

  @Test
  fun venvTest() {
    val venvSdk = venvFixture.get()
    Assertions.assertEquals(venvSdk, ProjectJdkTable.getInstance().findJdk(venvSdk.name))
  }
}