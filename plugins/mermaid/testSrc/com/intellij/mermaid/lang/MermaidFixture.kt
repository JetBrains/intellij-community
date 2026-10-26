package com.intellij.mermaid.lang

import com.intellij.openapi.project.Project
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertNotNull

/**
 * Creates a [MermaidFixture] on a new module of [projectFixture]. It is the JUnit 5 replacement of the JUnit 3 base `MermaidBaseTestCase`.
 * Declare the result as an instance property, so that each test gets its own fixture.
 * Declare [projectFixture] as a static field, so that the tests of the class share the project.
 * [testDataSubPath] replaces the constructor argument of `MermaidBaseTestCase`.
 * The test data path of [MermaidFixture.fixture] is the [testDataSubPath] directory under [MermaidTestingUtil.TEST_DATA_PATH].
 */
@TestOnly
fun mermaidFixture(
  projectFixture: TestFixture<Project>,
  testDataSubPath: String,
): TestFixture<MermaidFixture> = testFixture("mermaidFixture") { context ->
  val fixture = codeInsightModuleFixture(projectFixture).init()
  fixture.testDataPath = "${MermaidTestingUtil.TEST_DATA_PATH}/$testDataSubPath"
  initialized(MermaidFixture(fixture, context.extensionContext.requiredTestMethod.name)) {}
}

/**
 * The helpers of `MermaidBaseTestCase` for a JUnit 5 test.
 * Call a member from a background thread.
 */
@TestOnly
class MermaidFixture internal constructor(
  val fixture: CodeInsightTestFixture,
  private val testMethodName: String,
) {
  /**
   * The project of [fixture].
   */
  val project: Project get() = fixture.project

  /**
   * The test data directory of the test class.
   */
  val testDataPath: String get() = fixture.testDataPath

  /**
   * Returns the name of the test method without the `test` prefix.
   * The name has no leading spaces, and an underscore replaces each other space.
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String = MermaidTestingUtil.getTestName(testMethodName, lowercaseFirstLetter)

  /**
   * Finds the quick fix with the [text] among all the quick fixes of the configured file.
   * Then checks the preview of the quick fix and applies it on the EDT.
   * Fails when the file has no such quick fix.
   */
  fun checkPreviewAndLaunchQuickFix(text: String) {
    val fix = fixture.getAllQuickFixes().find { it.text == text }
    assertNotNull(fix, "No quick fix '$text'")
    runInEdtAndWait {
      fixture.checkPreviewAndLaunchAction(fix!!)
    }
  }
}
