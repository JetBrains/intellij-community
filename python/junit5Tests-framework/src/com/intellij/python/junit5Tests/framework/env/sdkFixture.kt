package com.intellij.python.junit5Tests.framework.env

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.sdkFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.jetbrains.python.PyNames
import com.jetbrains.python.sdk.PythonSdkAdditionalData
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
 * Create mock (not a real python, but with [homePath]) SDK.
 * The SDK has [PythonSdkAdditionalData], because the product treats a Python SDK without it as broken.
 */
fun TestFixture<Project>.pyMockSdkFixture(homePath: TestFixture<Path>): TestFixture<Sdk> = testFixture {
  val sdk = this@pyMockSdkFixture.sdkFixture("PyMockSDK" + System.currentTimeMillis().toString(), PyMockSdkTypeId, homePath).init()
  edtWriteAction {
    val modificator = sdk.sdkModificator
    modificator.sdkAdditionalData = PythonSdkAdditionalData(null)
    modificator.commitChanges()
  }
  initialized(sdk) {}
}

private object PyMockSdkTypeId : SdkTypeId {
  override fun getName(): String = PyNames.PYTHON_SDK_ID_NAME

  override fun getVersionString(sdk: Sdk): String? = null

  override fun saveAdditionalData(additionalData: SdkAdditionalData, additional: Element) = Unit

  override fun loadAdditionalData(
    currentSdk: Sdk,
    additional: Element,
  ): SdkAdditionalData? = null
}