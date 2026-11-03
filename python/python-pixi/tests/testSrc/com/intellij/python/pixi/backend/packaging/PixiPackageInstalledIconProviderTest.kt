// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.python.pixi.common.icons.PythonPixiCommonIcons
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.toolwindow.packages.tree.renderers.PyPackageInstalledIconProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
@Subsystems.PackagingRequirements
@Layers.Functional
internal class PixiPackageInstalledIconProviderTest {
  private val projectFixture = projectFixture()

  @Test
  fun `native Pixi packages retain their provenance icon`(@TestDisposable disposable: Disposable) {
    val repositories = PixiRepositoryManager(projectFixture.get(), PixiTestPythonRepositoryManager(projectFixture.get())) {
      PyResult.success(emptyList())
    }
      .also { Disposer.register(disposable, it) }
    val pkg = object : PythonPackage("native-probe", "1.0", false) {
      override val defaultRepository: PyPackageRepository = repositories.nativeRepository
    }

    val icon = PyPackageInstalledIconProvider.EP_NAME.extensionList.firstNotNullOfOrNull { it.iconFor(pkg) }

    assertThat(icon).isSameAs(PythonPixiCommonIcons.Pixi)
  }

  @Test
  fun `Python packages retain their default installed icon`() {
    val pkg = PythonPackage("python-probe", "1.0", false)

    val icon = PyPackageInstalledIconProvider.EP_NAME.extensionList.firstNotNullOfOrNull { it.iconFor(pkg) }

    assertThat(icon).isNull()
  }
}
