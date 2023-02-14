// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.conda

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.ProjectRule
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.conda.CondaEnvSdkFlavor
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.jetbrains.python.sdk.flavors.conda.PyCondaFlavorData
import org.jdom.Element
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class PyCondaAdditionalDataTest {


  @JvmField
  @Rule
  val projectRule: ProjectRule = ProjectRule()

  @Test
  fun testSerialize() {
    val flavorData = PyCondaFlavorData(PyCondaEnv(PyCondaEnvIdentity.NamedEnv("D"), "foo"))
    val data = PythonSdkAdditionalData(
      PyFlavorAndData(flavorData, CondaEnvSdkFlavor.getInstance()))
    val rootElement = Element("root")
    data.save(rootElement)

    val sdk = mock(Sdk::class.java)
    `when`(sdk.homePath).thenReturn("foo")


    val reloadedData = PythonSdkAdditionalData.loadFromElement(rootElement)
    Assert.assertEquals(reloadedData.flavor, CondaEnvSdkFlavor.getInstance())
    Assert.assertEquals(reloadedData.flavorAndData.data, flavorData)
  }
}