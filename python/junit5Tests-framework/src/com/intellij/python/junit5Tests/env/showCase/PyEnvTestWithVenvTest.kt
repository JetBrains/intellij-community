package com.intellij.python.junit5Tests.env.showCase

import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pySdkFixture
import com.intellij.python.junit5Tests.framework.env.pyVenvFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@PyEnvTestCase
class PyEnvTestWithVenvTest {
  private val tempPathFixture = tempPathFixture()
  private val moduleFixture = projectFixture().moduleFixture()
  private val venvFixture = pySdkFixture().pyVenvFixture(
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