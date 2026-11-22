// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.execution.target.TargetEnvironmentsManager
import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.UnixPythonSdkFlavor
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.PythonLanguageRuntimeConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestFor(issues = ["PY-93894", "PY-94164", "PY-93079"])
@Subsystems.RemoteInterpreters
@Layers.Functional
@TestApplication
internal class RegisterTargetTest {
  private val projectFixture = projectFixture()

  @BeforeEach
  fun registerTargetType(@TestDisposable disposable: Disposable) {
    TargetEnvironmentType.EXTENSION_NAME.point.registerExtension(TestTargetType(), disposable)
  }

  @Test
  fun `the targets of the project store a target of a Python SDK without its Python runtime`() {
    val project = projectFixture.get()
    val target = pythonSdkTarget(root = "/srv/app")

    registerTarget(project, target)

    val stored = TargetEnvironmentsManager.getInstance(project).state.targets.single { it.uuid == target.uuid }
    assertThat(stored.runtimes).isEmpty()
    val registered = findTarget(project, target.uuid) as TestTargetConfiguration
    assertThat(registered).isNotSameAs(target)
    assertThat(registered.projectRootOnTarget).isEqualTo("/srv/app")
    assertThat(registered.runtimes.resolvedConfigs()).isEmpty()
    assertThat(target.runtimes.findByType<PythonLanguageRuntimeConfiguration>()?.pythonInterpreterPath).isEqualTo("/usr/bin/python3")
  }

  @Test
  fun `a second registration of a target adds no second target`() {
    val project = projectFixture.get()
    val target = pythonSdkTarget(root = "/srv/app")

    registerTarget(project, target)
    registerTarget(project, target)

    assertThat(TargetEnvironmentsManager.getInstance(project).targets.resolvedConfigs().filter { it.uuid == target.uuid }).hasSize(1)
  }

  @Test
  fun `an edit of the target of an SDK replaces the target of the project`() {
    val project = projectFixture.get()
    val target = pythonSdkTarget(root = "/srv/app")
    registerTarget(project, target)

    // The edit dialog edits the target of the SDK in place, and the project holds a copy of it.
    target.projectRootOnTarget = "/srv/edited"
    replaceTarget(project, target)

    val targets = TargetEnvironmentsManager.getInstance(project).targets.resolvedConfigs().filter { it.uuid == target.uuid }
    val replaced = targets.single() as TestTargetConfiguration
    assertThat(replaced.projectRootOnTarget).isEqualTo("/srv/edited")
    assertThat(replaced.runtimes.resolvedConfigs()).isEmpty()
  }

  @Test
  fun `a replace of a target that the project does not hold registers it`() {
    val project = projectFixture.get()
    val target = pythonSdkTarget(root = "/srv/app")

    replaceTarget(project, target)

    assertThat((findTarget(project, target.uuid) as TestTargetConfiguration).projectRootOnTarget).isEqualTo("/srv/app")
  }

  @Test
  fun `the data of a target SDK keeps the id of its target across a save and a load`() {
    val target = pythonSdkTarget(root = "/srv/app")
    val element = Element("sdk-data")
    targetSdkData(target).save(element)

    val loaded = targetSdkData(null).apply { load(element) }

    // A commit of an SDK saves and loads its data, and the ref of the interpreter names the target by this id.
    val loadedTarget = loaded.targetEnvironmentConfiguration as TestTargetConfiguration
    assertThat(loadedTarget.uuid).isEqualTo(target.uuid)
    assertThat(loadedTarget.projectRootOnTarget).isEqualTo("/srv/app")
    assertThat(loadedTarget.runtimes.resolvedConfigs().filterIsInstance<PythonLanguageRuntimeConfiguration>())
      .describedAs("the load adds the Python runtime, so the saved state holds none")
      .hasSize(1)
  }

  private fun targetSdkData(target: TargetEnvironmentConfiguration?): PyTargetAwareAdditionalData =
    PyTargetAwareAdditionalData(PyFlavorAndData(PyFlavorData.Empty, UnixPythonSdkFlavor.getInstance()), Path.of(""), target).apply {
      interpreterPath = "/usr/bin/python3"
    }
}
