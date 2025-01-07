package com.intellij.python.junit5Tests.framework.env

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.jetbrains.python.sdk.configuration.createVirtualEnvSynchronously
import com.jetbrains.python.sdk.persist
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.tools.PythonType
import com.jetbrains.python.tools.SdkCreationRequest.LocalPython
import com.jetbrains.python.tools.createSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.pathString

typealias PySdkFixture = TestFixture<Sdk>

/**
 * Creates [Sdk] (if you only need a python path, use [PythonBinaryPath] or [CondaEnv])
 */
fun pySdkFixture(
  pythonType: PythonType<*> = PythonType.VanillaPython3,
): PySdkFixture = testFixture {
  val (sdk, autoClosable) = createSdk(LocalPython(pythonType))
  sdk.persist()
  initialized(sdk) {
    writeAction {
      ProjectJdkTable.getInstance().removeJdk(sdk)
      autoClosable.close()
    }
  }
}

/**
 * Create virtual env in [where]. If [addToSdkTable] then also added to the project jdk table
 */
fun PySdkFixture.pyVenvFixture(
  where: TestFixture<Path>,
  addToSdkTable: Boolean,
  moduleFixture: TestFixture<Module>? = null,
): TestFixture<Sdk> = testFixture {
  val baseSdk = this@pyVenvFixture.init()
  withContext(Dispatchers.EDT) {
    val module = moduleFixture?.init()
    val pathString = where.init().pathString
    val venvSdk = writeIntentReadAction {
      createVirtualEnvSynchronously(baseSdk,
                                    emptyList(),
                                    pathString,
                                    null,
                                    module?.project,
                                    module
      )
    }
    if (addToSdkTable) {
      venvSdk.persist()
      if (module != null) {
        module.pythonSdk = venvSdk
      }
    }
    initialized(venvSdk) {
      writeAction {
        ProjectJdkTable.getInstance().removeJdk(venvSdk)
      }
    }
  }
}

