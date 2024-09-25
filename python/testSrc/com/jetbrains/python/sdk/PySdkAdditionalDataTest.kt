// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.testFramework.ProjectRule
import com.jetbrains.python.remote.PyRemoteSdkAdditionalData

import org.jdom.Element
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

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

  @Test
  fun remoteAdditionalDataTest() {
    var data = PyRemoteSdkAdditionalData("!!!!!")

    data = PyRemoteSdkAdditionalData("sftp://host:22/home/bin/python3")
    data = PyRemoteSdkAdditionalData("docker://django:latest/python")
  }
}