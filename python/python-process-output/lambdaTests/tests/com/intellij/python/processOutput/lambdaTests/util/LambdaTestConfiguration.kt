// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.processOutput.lambdaTests.util

import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.ide.starter.testContextCache.CachedProjectConfiguration
import com.intellij.lambda.testFramework.starter.LambdaReusedSessionStrategy
import com.intellij.lambda.testFramework.utils.LambdaTestPluginHolder
import com.intellij.tools.ide.starter.product.pycharm.PyCharm
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * The cached project this module's lambda tests reuse: PyCharm instead of the framework's default IDEA Ultimate.
 * PyCharm Professional rather than Community: a split run needs an IDE that can act as the remote-dev backend.
 *
 * `NoProject` because the process-output topic is application-level — nothing here needs an open project.
 * Every test class in this module shares this single instance, because a separate instance is a separate cache entry.
 */
internal val pythonProcessOutputLambdaConfiguration: CachedProjectConfiguration = CachedProjectConfiguration(
  testCase = (object : TestCaseTemplate(IdeInfo.PyCharm) {}).withProject(NoProject),
  setup = {
    applyVMOptionsPatch {
      // Off because the frontend's dev build cannot load the model: `PythonCore` requires the content
      // module `intellij.libraries.mlapi.catboost`, whose `ModelComponentsProvider` lives in
      // `intellij.libraries.mlapi.core`, and a content module is private to its own plugin — adding the LLM
      // plugin, which declares core, does not put it on PythonCore's class path. Left on, the frontend
      // aborts startup in `ImportsRankingModelService` before any test code runs. Import ranking has
      // nothing to do with the process-output topic under test here.
      //
      // The whole option list is passed, not the bare word: `RegistryValue.selectedOption` parses
      // `[a|b|c*]` and returns null for anything else, and the reader of this key does `requireNotNull`.
      addSystemProperty(IMPORT_RANKING_ML_KEY, "[IN_EXPERIMENT|ENABLED|DISABLED*]")
    }
  },
)

/**
 * Installs this module's test plugin, `intellij.python.processOutput.lambdaTests.plugin`, into both IDEs of a split
 * run. Without it the frontend has no `intellij.lambda.testFramework` classes, and every `runInFrontend` fails with
 * `NoClassDefFoundError`.
 */
internal class PythonProcessOutputLambdaStrategy : LambdaReusedSessionStrategy() {
  override fun lambdaTestSetup(context: ExtensionContext): LambdaTestPluginHolder.LambdaTestSetup =
    LambdaTestPluginHolder.allModesPlugin("intellij.python.processOutput.lambdaTests")
}

private const val IMPORT_RANKING_ML_KEY: String = "quickfix.ranking.ml"
