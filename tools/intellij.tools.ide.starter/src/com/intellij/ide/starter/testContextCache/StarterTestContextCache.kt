package com.intellij.ide.starter.testContextCache

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.testContextCache.utils.currentTestContextDirName
import com.intellij.tools.ide.util.common.logOutput

/**
 * Creates a context of the current test over the prepared project of [configuration].
 *
 * For a test that starts and stops an IDE of its own. The project files go back to the prepared state first, so the
 * test starts from the state that [CachedProjectConfiguration.setup] left, whatever the test before it did. The
 * context has its own `config` and `plugins`, and the one shared `system` directory of the cached context.
 *
 * The other way to reuse a prepared project is `@ReusedRun`, where one IDE serves a whole test class.
 */
fun Starter.newTestContextFromCache(configuration: CachedProjectConfiguration): IDETestContext {
  val cachedTestContext = TestContextCache.getOrCreate(configuration) { contextName, _ ->
    newPreparedContext(contextName, configuration)
  }
  val restored = cachedTestContext.restorePreparedProjectFiles()
  if (restored) {
    cachedTestContext.requestProjectFilesVfsRefresh()
  }
  val currentTestContext = cachedTestContext.createCurrentTestContext(currentTestContextDirName())
  // the IDE takes in the rewound files before the other commands, see AT-5404
  currentTestContext.syncProjectFilesOnStart = restored
  logOutput(
    "The current test runs on the context at ${currentTestContext.paths.testHome}, " +
    "over the project prepared at ${cachedTestContext.testContext.paths.testHome}"
  )
  return currentTestContext
}

/** Creates the prepared context of a cache entry for [configuration] in [contextName], and runs its setup. */
internal fun Starter.newPreparedContext(contextName: String, configuration: CachedProjectConfiguration): IDETestContext =
  newTestContainer().newContext(
    testName = contextName,
    testCase = configuration.testCase,
    preserveSystemDir = false,
    projectHome = configuration.testCase.projectInfo.downloadAndUnpackProject(),
    ideDataPathsProvider = TestContextCache.cachedContextDataPathsProvider(),
  ).let(configuration.setup)
