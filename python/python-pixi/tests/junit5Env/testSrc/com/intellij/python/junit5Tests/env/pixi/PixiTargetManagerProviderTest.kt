// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.pixi

import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.Disposer
import com.intellij.python.junit5Tests.env.pixi.PixiTargetRuntimeTest.StagedTargetConfiguration
import com.intellij.python.junit5Tests.env.pixi.PixiTargetRuntimeTest.StagedTargetType
import com.intellij.python.pixi.backend.add.PixiAddInterpreterProvider
import com.intellij.python.pixi.backend.packaging.PixiPackageManager
import com.intellij.python.pixi.backend.packaging.PixiPackageManagerProvider
import com.intellij.python.pixi.backend.sdk.PixiSdkFlavor
import com.intellij.python.sdk.backend.TargetPythonInterpreter
import com.intellij.python.sdk.backend.pythonInterpreterResultAsync
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.add.v2.FileSystemFactory
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@TestApplication
@Subsystems.Interpreters
@Layers.Functional
internal class PixiTargetManagerProviderTest {
  private val disposable = disposableFixture()
  private val project = projectFixture()

  @Test
  fun `the plugin offers Pixi for a target interpreter`(@TempDir localRoot: Path): Unit = timeoutRunBlocking {
    val config = StagedTargetConfiguration(localRoot, localRoot.resolve("target"))
    val targetTypes = TargetEnvironmentType.EXTENSION_NAME
    ExtensionTestUtil.maskExtensions(targetTypes, targetTypes.extensionList + StagedTargetType(config), disposable.get())
    val fileSystem = FileSystemFactory.getInstance().create(config)
    val python = config.remoteRoot.resolve(".pixi/envs/docs/bin/python").toString()
    val data = PyTargetAwareAdditionalData(PyFlavorAndData(PyFlavorData.Empty, PixiSdkFlavor), localRoot, config)
    val sdk = ProjectJdkTable.getInstance().createSdk("Pixi target", PythonSdkType.getInstance())
    edtWriteAction {
      sdk.sdkModificator.apply {
        homePath = python
        sdkAdditionalData = data
        commitChanges()
      }
    }
    val interpreter = sdk.pythonInterpreterResultAsync().getOrThrow()

    assertThat(interpreter).isInstanceOf(TargetPythonInterpreter::class.java)
    assertThat(interpreter.ref.envRef.value).isEqualTo("docs")
    assertThat(PixiAddInterpreterProvider().manager.supports(fileSystem)).isTrue()
    val manager = PixiPackageManagerProvider().createPackageManager(project.get(), interpreter)
    assertThat(manager).isInstanceOf(PixiPackageManager::class.java)
    Disposer.register(disposable.get(), checkNotNull(manager))
  }
}
