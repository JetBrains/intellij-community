// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.python.venv.sdk.flavors.VirtualEnvSdkFlavor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems

import com.intellij.testFramework.ProjectRule
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData

import org.jdom.Element
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

@Subsystems.Interpreters
@Layers.Functional
class PySdkAdditionalDataTest {
  @JvmField
  @Rule
  val project = ProjectRule()

  /**
   * Ensure data saved and loaded correctly
   */
  @Test
  fun saveLoadTest() {
    val sut = PythonSdkAdditionalData(
      PyFlavorAndData(PyFlavorData.Empty, VirtualEnvSdkFlavor.getInstance()),
      project.module.baseDir?.path?.toNioPathOrNull()!!
    )

    val element = Element("root")
    sut.save(element)
    val loadedSut = PythonSdkAdditionalData.loadFromElement(element)
    Assert.assertEquals("UUID hasn't been loaded", sut.uuid, loadedSut.uuid)
  }

}