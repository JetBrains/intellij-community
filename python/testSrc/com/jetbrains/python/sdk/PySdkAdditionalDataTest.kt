// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.ProjectRule
import org.jdom.Element
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class PySdkAdditionalDataTest {
  @JvmField
  @Rule
  val project = ProjectRule()

  /**
   * Ensure data saved and loaded correctly
   */
  @Test
  fun saveLoadTest() {
    val sut = PythonSdkAdditionalData()

    val element = Element("root")
    sut.save(element)
    val loadedSut = PythonSdkAdditionalData.loadFromElement(element)
    Assert.assertEquals("UUID hasn't been loaded", sut.uuid, loadedSut.uuid)
  }
}