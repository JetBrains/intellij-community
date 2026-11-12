// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package de.plushnikov.intellij.plugin

import com.intellij.modcommand.ModCommandAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import de.plushnikov.intellij.plugin.LombokParsingFixture.ModeRunnerType
import de.plushnikov.intellij.plugin.action.LombokActionFixture
import de.plushnikov.intellij.plugin.intention.LombokIntentionFixture
import org.jetbrains.annotations.TestOnly
import java.util.function.BooleanSupplier
import java.util.function.Function
import java.util.function.Supplier

/**
 * Creates the code insight fixture of a Lombok test. Declare it as an instance field.
 *
 * The test data path is [basePath] in the Lombok test data, as [LombokTestUtil.getTestDataPath] gives it.
 * Give the project fixture the descriptor of the test, usually [LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR].
 */
@TestOnly
@JvmOverloads
fun lombokCodeInsightFixture(
  projectFixture: TestFixture<LightJavaProject>,
  basePath: String = "",
): TestFixture<LightJavaCodeInsightTestFixture> = lombokCodeInsightFixture(projectFixture, null, basePath)

/**
 * Creates the code insight fixture of a Lombok test as the other overload does, for a class that picks the descriptor for each test.
 *
 * [descriptor] gets the name of the test method without the `test` prefix, and returns the descriptor of the test.
 * See [lightJavaCodeInsightFixture] for the cost of a descriptor that is not the descriptor of [projectFixture].
 */
@TestOnly
@JvmOverloads
fun lombokCodeInsightFixture(
  projectFixture: TestFixture<LightJavaProject>,
  descriptor: Function<String, LightProjectDescriptor>?,
  basePath: String = "",
): TestFixture<LightJavaCodeInsightTestFixture> = testFixture("lombokCodeInsightFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture, descriptor, basePath = "").init()
  fixture.testDataPath = LombokTestUtil.getTestDataPath() + basePath
  initialized(fixture) {}
}

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

/**
 * Creates the [LombokActionFixture] of an action test. Declare it as an instance field.
 *
 * [action] creates the action of the test for each run. The test data path is [basePath] in the Lombok test data.
 */
@TestOnly
fun lombokActionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  basePath: String,
  action: Supplier<out AnAction>,
): TestFixture<LombokActionFixture> = testFixture("lombokActionFixture") {
  val fixture = lombokCodeInsightFixture(projectFixture, basePath).init()
  initialized(LombokActionFixture(fixture, action)) {}
}

/**
 * Creates the [LombokIntentionFixture] of an intention test. Declare it as an instance field.
 *
 * [action] creates the intention of the test. [invocationCheck] returns true when the intention changed the file as expected.
 * The test data path is [basePath] in the Lombok test data. It usually starts with [LombokIntentionFixture.TEST_DATA_INTENTION_DIRECTORY].
 */
@TestOnly
fun lombokIntentionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  basePath: String,
  action: Supplier<out ModCommandAction>,
  invocationCheck: BooleanSupplier,
): TestFixture<LombokIntentionFixture> = testFixture("lombokIntentionFixture") {
  val fixture = lombokCodeInsightFixture(projectFixture, basePath).init()
  initialized(LombokIntentionFixture(fixture, action, invocationCheck)) {}
}
