package com.intellij.python.junit5Tests.framework.env

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.sdkFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import com.jetbrains.python.PyNames
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import org.jdom.Element
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.python.sdk.backend.isInterpreterLibraryFlowEnabled

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

internal object PyMockSdkTypeId : SdkTypeId {
  override fun getName(): String = PyNames.PYTHON_SDK_ID_NAME

  override fun getVersionString(sdk: Sdk): String? = null

  override fun saveAdditionalData(additionalData: SdkAdditionalData, additional: Element) = Unit

  override fun loadAdditionalData(
    currentSdk: Sdk,
    additional: Element,
  ): SdkAdditionalData? = null
}

/**
 * The interpreter that the registry of [project] holds for this SDK. In the interpreter library flow the registry holds
 * the SDK itself, see [PythonInterpreterRegistry.putTestSdk]. In the legacy flow the SDK is in the SDK table: an SDK that
 * is not there yet goes there until the project closes, see [PythonInterpreterRegistry.addMockPythonInterpreter]. A run configuration
 * names its interpreter, so a test gives it this, not the SDK. An SDK without an owner directory gets [project]'s base
 * directory, as a mock interpreter does, see `PythonInterpreterRegistry.addMockPythonInterpreter`. Waits until a
 * recompute of the registry sees the SDK as it is now.
 */
@TestOnly
suspend fun Sdk.pythonInterpreterForTests(project: Project): PythonInterpreter {
  val data = sdkAdditionalData as PythonSdkAdditionalData
  if (data.associatedModulePath.isNullOrBlank()) {
    edtWriteAction {
      val modificator = sdkModificator
      (modificator.sdkAdditionalData as PythonSdkAdditionalData).associatedModulePath = checkNotNull(project.basePath)
      modificator.commitChanges()
    }
  }
  val registry = PythonInterpreterRegistry.getInstance(project)
  val ownerDir = Path.of(checkNotNull((sdkAdditionalData as PythonSdkAdditionalData).associatedModulePath))
  if (isInterpreterLibraryFlowEnabled()) {
    // The interpreter library flow reads no SDK table, so the registry holds the SDK itself.
    registry.putTestSdk(this, ownerDir)
  }
  else if (ProjectJdkTable.getInstance().allJdks.none { it === this }) {
    // The legacy flow reads the SDK table. The registry adds the SDK there and removes it with the project.
    return registry.addMockPythonInterpreter(ownerDir, this)
  }
  return withTimeout(DEFAULT_TEST_TIMEOUT) {
    var interpreter = registry.interpreterOf(this@pythonInterpreterForTests)
    while (interpreter == null) {
      delay(50.milliseconds)
      interpreter = registry.interpreterOf(this@pythonInterpreterForTests)
    }
    interpreter
  }
}
