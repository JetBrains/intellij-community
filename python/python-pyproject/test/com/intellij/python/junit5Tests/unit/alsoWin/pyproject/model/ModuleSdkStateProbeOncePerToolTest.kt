// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.modules
import com.intellij.openapi.util.Disposer
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.junit5Tests.framework.PyDefaultTestApplication
import com.intellij.python.pyproject.model.api.ModuleSdkState
import com.intellij.python.pyproject.model.api.SdkForModuleConfigInstruction
import com.intellij.python.pyproject.model.api.getModuleSdkState
import com.intellij.python.pyproject.model.internal.SuggestedSdk
import com.intellij.python.pyproject.model.internal.suggestSdk
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.EnvCheckerResult
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.configuration.PyProjectTomlConfigurationExtension
import com.jetbrains.python.sdk.configuration.prepareSdkCreator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds


private const val FROM_THE_PROBE = "from-the-probe"
private const val FROM_THE_SECOND_ASK = "from-the-second-ask"

/**
 * A configurator is asked at most once per [getModuleSdkState].
 *
 * The state is computed in two steps: every configurator is probed, and then a tool the `pyproject.toml` declares
 * gets asked again through the entry point that skips the toml precondition. Answering either way lets the
 * configurator *run its tool* — `poetry check --lock` and friends — so asking the same one twice means running the
 * tool twice, which is both a duplicated entry in the Python Process Output window and doubled interpreter-setup
 * latency. The second ask exists for a tool that declined on toml grounds; one that already answered does not need it.
 */
@PyDefaultTestApplication
internal class ModuleSdkStateProbeOncePerToolTest {

  private val f by pyProjectTomlSyncFixture()

  /**
   * Records which module each entry point was taken for, and labels its answer so the two are told apart in the
   * result. Which modules appear is asserted, never how often: opening a project also configures its SDK in the
   * background, so the same module legitimately gets probed again from outside this test. What must never happen is
   * the *second* entry point being taken for a module whose tool the probe already answered for — and that holds
   * however many times the background asks, since it runs the same code.
   *
   * [EnvCheckerResult.EnvNotFound] becomes a [CreateSdkInfo.WillCreateEnv] — deliberately not an `ExistingEnv`,
   * which would short-circuit before the second ask and make the test pass for the wrong reason.
   */
  private class CountingConfigurator(override val toolId: ToolId) : PyProjectTomlConfigurationExtension {
    val probed: MutableList<String> = CopyOnWriteArrayList()
    val askedAgain: MutableList<String> = CopyOnWriteArrayList()

    override val potentialDependencyFiles: Set<String> = emptySet()

    override suspend fun checkEnvironmentAndPrepareSdkCreator(module: Module, venvsInModule: List<PythonBinary>): CreateSdkInfo? {
      probed.add(module.name)
      return envNotFound(FROM_THE_PROBE)
    }

    override suspend fun createSdkWithoutPyProjectTomlChecks(module: Module, venvsInModule: List<PythonBinary>): CreateSdkInfo? {
      askedAgain.add(module.name)
      return envNotFound(FROM_THE_SECOND_ASK)
    }

    override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension = this

    private suspend fun envNotFound(intentionName: String): CreateSdkInfo? =
      prepareSdkCreator({ EnvCheckerResult.EnvNotFound(intentionName) }) { { Result.localizedError("never created") } }
  }

  @Test
  fun `a tool declared in pyproject toml is asked once`(): Unit = timeoutRunBlocking(60.seconds) {
    // `[tool.poetry]` is what makes the module tool-managed, so the declared tool ends up in `preferTools` and the
    // second ask has something to look at. Poetry 1 projects have no `[project]` table at all (PY-91765), but the
    // invariant under test is about the tool, not about how the table was found.
    edtWriteAction {
      f.root.writePyprojectToml("""
        [project]
        name = "asked-once"
        version = "0.1.0"

        [tool.poetry]
        name = "asked-once"
        version = "0.1.0"
      """.trimIndent())
    }
    f.reloadProject()

    val module = f.project.modules.single { it.name == "asked-once" }
    val declaredTool = when (val suggested = module.suggestSdk()) {
      is SuggestedSdk.PyProjectIndependent -> suggested.preferTools.single()
      is SuggestedSdk.SameAs, null ->
        fail("the toml declares a tool, so the module must be pyproject-managed, got $suggested")
    }

    val counting = CountingConfigurator(declaredTool)
    val disposable = Disposer.newDisposable()
    try {
      // Masked rather than added: as the only configurator on the point, this one owns both the probe's answer and
      // the second ask, so the counts below cannot be confused with a real tool's.
      ExtensionTestUtil.maskExtensions(PyProjectSdkConfigurationExtension.EP_NAME, listOf(counting), disposable)

      // `fresh` skips the shared cache, whose contents depend on what asked before this test.
      val state = module.getModuleSdkState(mapOf(declaredTool to counting), fresh = true)

      assertThat(counting.probed)
        .describedAs("the masked configurator should be the one answering, or the rest proves nothing")
        .contains(module.name)
      assertThat(counting.askedAgain)
        .describedAs("the probe answered for this tool, so nothing should have taken the second entry point")
        .doesNotContain(module.name)

      val answer = when (state) {
        is ModuleSdkState.HasSdk -> fail("the module should have no SDK yet, got ${state.sdk}")
        is ModuleSdkState.NoSdk -> when (val instruction = state.sdkConfigInstruction) {
          is SdkForModuleConfigInstruction.CreateSdkInfoWrapper -> instruction.createSdkInfoWithTool
          is SdkForModuleConfigInstruction.SameAs, null ->
            fail("the probe answered with a tool, so the instruction should wrap it, got $instruction")
        }
      }
      assertThat(answer.toolId).isEqualTo(declaredTool)
      assertThat(answer.createSdkInfo.intentionName)
        .describedAs("the answer should be the one the probe gave, reused rather than asked for again")
        .isEqualTo(FROM_THE_PROBE)
    }
    finally {
      Disposer.dispose(disposable)
    }
  }
}
