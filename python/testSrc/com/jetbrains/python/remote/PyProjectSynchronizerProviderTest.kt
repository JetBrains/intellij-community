// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.remote

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import com.intellij.remote.CredentialsType
import com.intellij.remote.ext.RemoteCredentialsHandler
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.TestApplicationManager
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.function.Consumer

class PyProjectSynchronizerProviderTest : LightPlatformTestCase() {
  override fun setUp() {
    super.setUp()

    TestApplicationManager.getInstance()
  }

  fun `test get synchronizer for local SDK`() {
    val sdk = mock(Sdk::class.java)
    val localSdkAdditionalData = mock(PythonSdkAdditionalData::class.java)
    `when`(sdk.sdkAdditionalData).thenReturn(localSdkAdditionalData)

    assertNull(PyProjectSynchronizerProvider.getSynchronizer(sdk))
  }

  fun `test get synchronizer for unsupported remote SDK`() {
    val sdk = mock(Sdk::class.java)
    val unsupportedRemoteSdkAdditionalData = mock(PyRemoteSdkAdditionalDataBase::class.java)
    `when`(unsupportedRemoteSdkAdditionalData.remoteConnectionType).thenReturn(TestCredentialsType.INSTANCE)
    `when`(sdk.sdkAdditionalData).thenReturn(unsupportedRemoteSdkAdditionalData)

    assertEquals(PyUnknownProjectSynchronizer.INSTANCE, PyProjectSynchronizerProvider.getSynchronizer(sdk))
  }

  fun `test get synchronizer for supported remote SDK`() {
    val sdk = mock(Sdk::class.java)
    val unsupportedRemoteSdkAdditionalData = mock(PyRemoteSdkAdditionalDataBase::class.java)
    `when`(unsupportedRemoteSdkAdditionalData.remoteConnectionType).thenReturn(TestCredentialsType.INSTANCE)
    `when`(sdk.sdkAdditionalData).thenReturn(unsupportedRemoteSdkAdditionalData)

    PyProjectSynchronizerProvider.EP_NAME.getPoint().registerExtension(PyTestProjectSynchronizerProvider(), testRootDisposable)

    assertEquals(PyTestProjectSynchronizer.INSTANCE, PyProjectSynchronizerProvider.getSynchronizer(sdk))
  }

  private class TestCredentialsType private constructor() : CredentialsType<TestCredentialsType>(TEST_CREDENTIALS_TYPE_NAME,
                                                                                                 TEST_CREDENTIALS_TYPE_PREFIX) {
    override fun getCredentialsKey(): Key<TestCredentialsType> = throwUnsupportedOperationException()

    override fun getHandler(credentials: TestCredentialsType?): RemoteCredentialsHandler = throwUnsupportedOperationException()

    override fun createCredentials(): TestCredentialsType = throwUnsupportedOperationException()

    companion object {
      private const val TEST_CREDENTIALS_TYPE_NAME = "Test Credentials Type"

      private const val TEST_CREDENTIALS_TYPE_PREFIX = "test://"

      val INSTANCE = TestCredentialsType()
    }
  }

  private class PyTestProjectSynchronizerProvider : PyProjectSynchronizerProvider {
    override fun getSynchronizer(credsType: CredentialsType<*>, sdk: Sdk): PyProjectSynchronizer? =
      if (credsType == TestCredentialsType.INSTANCE) PyTestProjectSynchronizer.INSTANCE else null
  }

  private class PyTestProjectSynchronizer private constructor() : PyProjectSynchronizer {
    override fun checkSynchronizationAvailable(syncCheckStrategy: PySyncCheckStrategy): String? = throwUnsupportedOperationException()

    override fun getDefaultRemotePath(): String? = throwUnsupportedOperationException()

    override fun syncProject(module: Module,
                             syncDirection: PySyncDirection,
                             callback: Consumer<Boolean>?,
                             vararg fileNames: String) = throwUnsupportedOperationException()

    override fun mapFilePath(project: Project, direction: PySyncDirection, filePath: String): String? = throwUnsupportedOperationException()

    companion object {
      val INSTANCE = PyTestProjectSynchronizer()
    }
  }

  companion object {
    private fun throwUnsupportedOperationException(): Nothing = throw UnsupportedOperationException()
  }
}