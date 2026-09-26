package com.intellij.python.junit5Tests.framework.env

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.sdkFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.jetbrains.python.PyNames
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Sdk with environment info. Use it as a regular sdk, but env fixtures (venv, conda) might use [env]
 */
@ApiStatus.Internal
class SdkFixture<ENV : Any>(val sdk: Sdk, val env: ENV) {
  override fun equals(other: Any?): Boolean {
    return this === other || other is SdkFixture<*> && other.sdk == sdk
  }

  override fun hashCode(): Int = sdk.hashCode()
}

/**
 * Create mock (not a real python, but with [homePath]) SDK
 */
fun TestFixture<Project>.pyMockSdkFixture(homePath: TestFixture<Path>) =
  sdkFixture("PyMockSDK" + System.currentTimeMillis().toString(), PyMockSdkTypeId, homePath)

private object PyMockSdkTypeId : SdkTypeId {
  override fun getName(): String = PyNames.PYTHON_SDK_ID_NAME

  override fun getVersionString(sdk: Sdk): String? = null

  override fun saveAdditionalData(additionalData: SdkAdditionalData, additional: Element) = Unit

  override fun loadAdditionalData(
    currentSdk: Sdk,
    additional: Element,
  ): SdkAdditionalData? = null
}