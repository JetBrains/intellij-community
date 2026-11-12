// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package de.plushnikov.intellij.plugin

import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import de.plushnikov.intellij.plugin.LombokParsingFixture.ModeRunnerType
import org.jetbrains.annotations.TestOnly

/**
 * Creates the [LombokParsingFixture] of a parsing test. Declare it as an instance field, and declare each test with [LombokParsingTest].
 *
 * The fixture reads the mode of the invocation of [LombokParsingTest]. A plain `@Test` runs in the normal mode.
 * In the incomplete mode, the test gets a module of [LombokParsingOptions.incompleteDescriptor] for this invocation.
 * The other modes use the descriptor of [projectFixture], usually [LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR].
 * The test data path is [basePath] in the Lombok test data.
 */
@TestOnly
@JvmOverloads
fun lombokParsingFixture(
  projectFixture: TestFixture<LightJavaProject>,
  basePath: String = "",
  options: LombokParsingOptions = LombokParsingOptions(),
): TestFixture<LombokParsingFixture> = lombokParsingFixture("lombokParsingFixture", projectFixture, basePath, options, false)

/**
 * Creates the [LombokParsingFixture] of a config system test, as [lombokParsingFixture] does.
 *
 * [LombokParsingFixture.doTest] lowercases the first letter of the test name. It copies `<path>/before/lombok.config` of the test data
 * to the project, and compares `<path>/before/<file>.java` with `<path>/after/<file>.java`.
 */
@TestOnly
@JvmOverloads
fun lombokConfigSystemFixture(
  projectFixture: TestFixture<LightJavaProject>,
  basePath: String = "",
  options: LombokParsingOptions = LombokParsingOptions(),
): TestFixture<LombokParsingFixture> = lombokParsingFixture("lombokConfigSystemFixture", projectFixture, basePath, options, true)

private fun lombokParsingFixture(
  debugString: String,
  projectFixture: TestFixture<LightJavaProject>,
  basePath: String,
  options: LombokParsingOptions,
  configSystem: Boolean,
): TestFixture<LombokParsingFixture> = testFixture(debugString) { context ->
  val mode = LombokParsingModes.getMode(context.extensionContext)
  val codeInsightFixture = if (mode == ModeRunnerType.INCOMPLETE) {
    lightJavaCodeInsightFixture(projectFixture, { options.incompleteDescriptor })
  }
  else {
    lightJavaCodeInsightFixture(projectFixture)
  }
  val fixture = codeInsightFixture.init()
  fixture.testDataPath = LombokTestUtil.getTestDataPath() + basePath
  initialized(LombokParsingFixture(fixture, mode, options, configSystem)) {}
}
