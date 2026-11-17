// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.concurrency.currentThreadContext
import com.intellij.execution.ExecutionException
import com.intellij.execution.Platform
import com.intellij.execution.target.BaseTargetEnvironmentRequest
import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.execution.target.TargetPlatform
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.contextModality
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.python.community.execService.impl.TargetEnvironmentRequestHandler
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.ui.EmptyIcon
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import com.jetbrains.python.run.target.PythonHelpersMappings
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.UnixPythonSdkFlavor
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Icon

/**
 * A new target interpreter asks its target for the version. The target must see the modality of the caller. A target
 * that waits for the UI thread with a different modality cannot finish under a modal dialog.
 */
@TestFor(issues = ["PY-94068"])
@Subsystems.RemoteInterpreters
@Layers.Functional
@TestApplication
internal class TargetInterpreterVersionContextTest {
  private val projectFixture = projectFixture()

  private val probeModality = AtomicReference<ModalityState?>()

  @BeforeEach
  fun registerTargetType(@TestDisposable disposable: Disposable) {
    TargetEnvironmentType.EXTENSION_NAME.point.registerExtension(TestTargetType(probeModality), disposable)
    val handlers = ExtensionPointName.create<TargetEnvironmentRequestHandler<*>>("Pythonid.execService.targetEnvironmentRequestHandler")
    ExtensionTestUtil.maskExtensions(handlers, handlers.extensionList + TestTargetRequestHandler(), disposable)
  }

  @Test
  fun `the version probe of a new target interpreter keeps the modality of the caller`(
    @TempDir ownerDir: Path,
  ): Unit = timeoutRunBlocking {
    val flavorAndData = PyFlavorAndData(PyFlavorData.Empty, UnixPythonSdkFlavor.getInstance())
    val data = PyTargetAwareAdditionalData(flavorAndData, ownerDir, TestTargetConfiguration()).also { it.interpreterPath = PYTHON_ON_TARGET }
    val project = projectFixture.get()
    val registry = PythonInterpreterRegistry.getInstance(project)

    val callerModality = withModalProgress(project, "Creating the interpreter") {
      registry.newPythonInterpreter(ownerDir, PathHolder.Target(PYTHON_ON_TARGET), data)
      currentThreadContext().contextModality()
    }

    assertThat(callerModality).isNotEqualTo(ModalityState.nonModal())
    assertThat(probeModality.get()).isSameAs(callerModality)
  }

  private class TestTargetConfiguration : TargetEnvironmentConfiguration(TEST_TARGET_ID),
                                          PersistentStateComponent<TestTargetConfiguration.State> {
    class State : BaseState() {
      var root by string()
    }

    override var projectRootOnTarget: String = ""

    override fun getState(): State = State().also { it.root = projectRootOnTarget }

    override fun loadState(state: State) {
      projectRootOnTarget = state.root.orEmpty()
    }
  }

  private class TestTargetType(private val probeModality: AtomicReference<ModalityState?>) :
    TargetEnvironmentType<TestTargetConfiguration>(TEST_TARGET_ID) {
    override val displayName: String = "Test"
    override val icon: Icon = EmptyIcon.ICON_16
    override fun createSerializer(config: TestTargetConfiguration): PersistentStateComponent<*> = config
    override fun createDefaultConfig(): TestTargetConfiguration = TestTargetConfiguration()
    override fun duplicateConfig(config: TestTargetConfiguration): TestTargetConfiguration = duplicateTargetConfiguration(this, config)

    override fun createEnvironmentRequest(project: Project?, config: TestTargetConfiguration): TargetEnvironmentRequest =
      TestTargetRequest(config, probeModality)

    override fun createConfigurable(
      project: Project,
      config: TestTargetConfiguration,
      defaultLanguage: LanguageRuntimeType<*>?,
      parentConfigurable: Configurable?,
    ): Configurable = throw UnsupportedOperationException("This test configures no target")
  }

  /**
   * Records the modality of the version probe, and then fails the probe with the declared [ExecutionException], so the
   * test starts no process.
   */
  private class TestTargetRequest(
    override val configuration: TestTargetConfiguration,
    private val probeModality: AtomicReference<ModalityState?>,
  ) : BaseTargetEnvironmentRequest() {
    override val targetPlatform: TargetPlatform = TargetPlatform(Platform.UNIX)

    override fun prepareEnvironment(progressIndicator: TargetProgressIndicator): TargetEnvironment {
      probeModality.set(currentThreadContext().contextModality())
      throw ExecutionException("This test prepares no environment")
    }
  }

  /** Maps no upload root to a persistent path, so the exec layer reaches [TestTargetRequest.prepareEnvironment]. */
  private class TestTargetRequestHandler : TargetEnvironmentRequestHandler<TestTargetRequest>(TestTargetRequest::class.java) {
    override suspend fun mapUploadRootsImpl(request: TestTargetRequest, localDirs: Set<Path>): UploadInfo = UploadInfo(
      object : HelpersAwareTargetEnvironmentRequest {
        override val targetEnvironmentRequest: TargetEnvironmentRequest = request
        override fun preparePyCharmHelpers(): PythonHelpersMappings = PythonHelpersMappings(emptyList())
      },
    )
  }

  private companion object {
    const val TEST_TARGET_ID = "py-target-interpreter-version-context-test"
    const val PYTHON_ON_TARGET = "/opt/venv/bin/python"
  }
}
