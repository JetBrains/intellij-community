// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.syntax.lambdaTests.util

import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.ide.starter.testContextCache.CachedProjectConfiguration
import com.intellij.lambda.testFramework.starter.LambdaReusedSessionStrategy
import com.intellij.lambda.testFramework.utils.LambdaTestPluginHolder
import com.intellij.python.syntax.lambdaTests.project.PythonLangLevelSyncProject
import com.intellij.tools.ide.starter.product.pycharm.PyCharm
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * The cached project this module's lambda tests reuse: PyCharm instead of the framework's default IDEA Ultimate.
 * PyCharm Professional rather than Community: a split run needs an IDE that can act as the remote-dev backend, and
 * this is also the product the fixed code (`python/frontend`, `python/frontend.base`) ships in.
 *
 * The project is [PythonLangLevelSyncProject] rather than `NoProject`: unlike
 * `python-process-output/lambdaTests` (an application-level topic, no file involved), this suite's claim is
 * about a per-`VirtualFile` value, so a real, stable file both sides can resolve is load-bearing here.
 * Every test class in this module shares this single instance, because a separate instance is a separate cache entry.
 */
internal val pythonLangLevelSyncLambdaConfiguration: CachedProjectConfiguration = CachedProjectConfiguration(
  testCase = (object : TestCaseTemplate(IdeInfo.PyCharm) {}).withProject(PythonLangLevelSyncProject),
  setup = {
    applyVMOptionsPatch {
      // Inherited from python-process-output/lambdaTests: off because the frontend's dev build cannot load
      // the model: `PythonCore` requires the content module `intellij.libraries.mlapi.catboost`, whose
      // `ModelComponentsProvider` lives in `intellij.libraries.mlapi.core`, and a content module is private
      // to its own plugin — adding the LLM plugin, which declares core, does not put it on PythonCore's
      // class path. Left on, the frontend aborts startup in `ImportsRankingModelService` before any test
      // code runs. Import ranking has nothing to do with language-level sync under test here, and this
      // suite starts the same PyCharm frontend dev build that hit the crash originally.
      //
      // The whole option list is passed, not the bare word: `RegistryValue.selectedOption` parses
      // `[a|b|c*]` and returns null for anything else, and the reader of this key does `requireNotNull`.
      addSystemProperty(IMPORT_RANKING_ML_KEY, "[IN_EXPERIMENT|ENABLED|DISABLED*]")
    }
  },
)

/**
 * Installs this module's test plugin, `intellij.python.syntax.lambdaTests.plugin`, into both IDEs of a split run.
 * Without it the frontend has no `intellij.lambda.testFramework` classes, and every `runInFrontend` fails with
 * `NoClassDefFoundError`.
 */
internal class PythonSyntaxLambdaStrategy : LambdaReusedSessionStrategy() {
  override fun lambdaTestSetup(context: ExtensionContext): LambdaTestPluginHolder.LambdaTestSetup =
    LambdaTestPluginHolder.allModesPlugin("intellij.python.syntax.lambdaTests")
}

private const val IMPORT_RANKING_ML_KEY: String = "quickfix.ranking.ml"
