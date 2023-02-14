// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.testFramework.ProjectRule
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import org.jdom.Element
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

class PySdkAdditionalDataSaveRestoreTest {

  @JvmField
  @Rule
  val projectRule: ProjectRule = ProjectRule()

  @Test
  fun test() = invokeAndWaitIfNeeded {
    val sdk = ProjectJdkImpl("mySdk", PythonSdkType.getInstance(), "path", "ver")
    val data = sdk.getOrCreateAdditionalData()
    val uuid = data.uuid
    val elem = Element("root")
    data.save(elem)

    val newData = PythonSdkAdditionalData.loadFromElement(elem)
    Assert.assertEquals("uuid didn't survive reloading", uuid, newData.uuid)
  }
}