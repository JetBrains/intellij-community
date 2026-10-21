// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.syntax.lambdaTests

import com.intellij.ide.starter.driver.reused.ReusedProject
import com.intellij.ide.starter.driver.reused.ReusedRun
import com.intellij.ide.starter.ide.IdeRunMode
import com.intellij.lambda.testFramework.junit.RunInMonolithAndSplitMode
import com.intellij.lambda.testFramework.junit.SetErrorIgnorerExtension
import com.intellij.lambda.testFramework.testApi.editor.getVirtualFileByRelativePath
import com.intellij.lambda.testFramework.testApi.editor.openFileAndWaitEditorSelected
import com.intellij.lambda.testFramework.testApi.editor.waitForSelectedEditor
import com.intellij.lambda.testFramework.testApi.getProject
import com.intellij.lambda.testFramework.utils.IdeWithLambda
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.roots.impl.FilePropertyPusher
import com.intellij.python.syntax.lambdaTests.util.PythonSyntaxLambdaStrategy
import com.intellij.python.syntax.lambdaTests.util.pythonLangLevelSyncLambdaConfiguration
import com.intellij.remoteDev.tests.impl.utils.waitSuspending
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.PyLanguageFacade
import com.jetbrains.python.psi.LanguageLevel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * PY-81981 ("Sync client language level with the backend"): on a split run, the Python language level that
 * `PyLanguageFacade.INSTANCE.getEffectiveLanguageLevel` reports for a file never reached the frontend — the
 * frontend process has no SDK/roots model of its own to compute it from, and before the fix nothing pushed the
 * backend's computed value across. The fix (commit 0d792e6b33b13cc3bec47a7133c3b34cbfd5b58e, "PY-81981 Sync
 * pushed language level") made `PythonLanguageLevelPusher.persistAttribute` on the backend publish the level so
 * the frontend's own `PyLanguageFacade` implementation (`PyFrontendLanguageFacade`) observes it. The revert that
 * makes this test red: drop that publish step from `persistAttribute` (or the frontend-side consumer wiring it
 * feeds), so `getEffectiveLanguageLevel` on the frontend falls back to whatever default it uses locally instead
 * of the value the backend actually pushed.
 *
 * Split only. `PyFrontendLanguageFacade` is declared in `python/plugin/resources/META-INF/plugin.xml` with
 * `required-if-available="intellij.platform.frontend.split.base"` — that module, and the class under test,
 * never load in a monolith process, so a monolith invocation would exercise `PyLanguageFacadeImpl` instead and
 * prove nothing about the bug this ticket fixed.
 *
 * `PYTHON_LEVEL` is deliberately `PYTHON39`: both `LanguageLevel.getDefault()` and `LanguageLevel.getLatest()`
 * currently resolve to `PYTHON315` (the last enum constant), so asserting against either of those would pass
 * even if the push never happened and the frontend just fell back to its own default — the frontend would
 * "coincidentally" report the right value either way. `PYTHON39` cannot be produced by that fallback, so
 * observing it on the frontend is only possible if the backend's pushed value actually arrived.
 *
 * The backend lambda below never names `PythonLanguageLevelPusher` (the real `persistAttribute` implementation,
 * in content module `intellij.python.psi.impl`) directly: that module is backend-only (absent from the
 * frontend's product layout), and this test's own content module — `intellij.python.syntax.lambdaTests`
 * — is the *same* module loaded on both sides of a split run. A hard `<dependencies>` edge from a shared content
 * module onto a dependency that is unavailable on one side silently excludes that module from that side
 * (confirmed against this exact test by a live run: once compiled against `intellij.python.psi.impl` and
 * invoked, the backend lambda failed with `NoClassDefFoundError: com/jetbrains/python/psi/impl/
 * PythonLanguageLevelPusher`, because the content module's own `<dependencies>` — correctly, deliberately —
 * never declares that module; see `community/python/python-process-output/lambdaTests/resources/
 * intellij.python.processOutput.lambdaTests.xml` for the prior precedent of this same silent-exclusion rule).
 * Instead, the lambda looks the pusher up dynamically through the shared, generic
 * `com.intellij.openapi.roots.impl.FilePropertyPusher` extension point (module `intellij.platform.projectModel`,
 * loaded on both frontend and backend) and calls `persistAttribute` — declared on the shared interface itself —
 * through that handle. No class literal or import of `PythonLanguageLevelPusher` ever appears in this module's
 * bytecode, so the module's own dependency set, and its loadability on the frontend, are unaffected; the
 * concrete backend-only class is resolved only via the already-active EP_NAME extension list that the real
 * product registers on the backend (`community/python/python-psi-impl/resources/intellij.python.psi.impl.xml`),
 * using that class's own module's classloader, never this test module's.
 */
@RunInMonolithAndSplitMode(IdeRunMode.SPLIT)
@ExtendWith(SetErrorIgnorerExtension::class)
@ReusedRun(sessionStrategy = PythonSyntaxLambdaStrategy::class)
internal class PyLanguageFacadeFrontendSyncTest {

  companion object {
    @ReusedProject
    private val project = pythonLangLevelSyncLambdaConfiguration
  }

  @Test
  fun `frontend PyLanguageFacade follows a level the backend pushes while the file is open`(ide: IdeWithLambda) =
    timeoutRunBlocking(TEST_TIMEOUT) {
      ide {
        // Open first, push second, and the order is the point. The backend provider re-reads the persisted
        // level on subscription, so a push that happens before the frontend is listening is delivered by that
        // initial read and the publish path is never exercised. Opening first puts
        // PythonLanguageLevelPusher.persistAttribute's publish on the critical path, which is what the fix added.
        runInBackend("Open $MAIN_PY_RELATIVE_PATH so the frontend receives it") {
          openFileAndWaitEditorSelected(getVirtualFileByRelativePath(MAIN_PY_RELATIVE_PATH), getProject())
        }

        val levelBeforePush = runInFrontendGetResult("Read the level the frontend starts with") {
          val virtualFile = waitForSelectedEditor(MAIN_PY_RELATIVE_PATH).file
          PyLanguageFacade.INSTANCE.getEffectiveLanguageLevel(getProject(), virtualFile)
        } as LanguageLevel

        runInBackend("Push $PYTHON_LEVEL onto $MAIN_PY_RELATIVE_PATH and publish the change") {
          val project = getProject()
          val virtualFile = getVirtualFileByRelativePath(MAIN_PY_RELATIVE_PATH)

          @Suppress("UNCHECKED_CAST")
          val languageLevelPusher = FilePropertyPusher.EP_NAME.extensionList
            .firstOrNull { it.javaClass.name == PYTHON_LANGUAGE_LEVEL_PUSHER_CLASS_NAME }
            as? FilePropertyPusher<LanguageLevel>
            ?: error(
              "No FilePropertyPusher.EP_NAME extension named $PYTHON_LANGUAGE_LEVEL_PUSHER_CLASS_NAME is " +
                "registered on the backend; registered: ${FilePropertyPusher.EP_NAME.extensionList.map { it.javaClass.name }}",
            )
          // persistAttribute asserts read access through PushedFilePropertiesUpdaterImpl.filePropertiesChanged,
          // and the lambda host runs this on a worker thread with no lock held, so the wrap is required.
          runReadAction {
            languageLevelPusher.persistAttribute(project, virtualFile, PYTHON_LEVEL)
          }
        }

        val levelAfterPush = runInFrontendGetResult(
          "Wait for the frontend facade to observe the pushed level", timeout = TEST_TIMEOUT,
        ) {
          val project = getProject()
          val virtualFile = waitForSelectedEditor(MAIN_PY_RELATIVE_PATH).file
          waitSuspending("PyLanguageFacade.INSTANCE.getEffectiveLanguageLevel reaches $PYTHON_LEVEL", 30.seconds) {
            PyLanguageFacade.INSTANCE.getEffectiveLanguageLevel(project, virtualFile) == PYTHON_LEVEL
          }
          PyLanguageFacade.INSTANCE.getEffectiveLanguageLevel(project, virtualFile)
        } as LanguageLevel

        assertThat(levelBeforePush)
          .describedAs("the level the frontend reported before the push — if this already equals %s, the test " +
                       "cannot tell the update path from the initial read", PYTHON_LEVEL)
          .isNotEqualTo(PYTHON_LEVEL)
        assertThat(levelAfterPush)
          .describedAs("PyLanguageFacade.getEffectiveLanguageLevel(project, %s) on the split frontend, after the " +
                       "backend persisted and published %s while the file was already open",
                       MAIN_PY_RELATIVE_PATH, PYTHON_LEVEL)
          .isEqualTo(PYTHON_LEVEL)
      }
    }
}

private val TEST_TIMEOUT = 2.minutes
private val PYTHON_LEVEL = LanguageLevel.PYTHON39
private const val MAIN_PY_RELATIVE_PATH = "src/main.py"
private const val PYTHON_LANGUAGE_LEVEL_PUSHER_CLASS_NAME = "com.jetbrains.python.psi.impl.PythonLanguageLevelPusher"
