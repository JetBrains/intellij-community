// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.maven.testFramework.utils.MavenProjectJDKTestFixture
import com.intellij.openapi.application.WriteAction
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.execute
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Integration test for the Surefire report pipeline: Maven execution → XML report → TC service messages.
 *
 * Unlike [MavenJUnitDelegatedTestReactorTest] (which tests parameter assembly), this test exercises
 * [SurefireReportParser] against XML produced by a real Maven/Surefire run, verifying that passing
 * and failing tests produce the correct TC message sequence.
 */
@TestApplication
class MavenSurefireReportTest {

  private val maven by mavenImportingFixture(skipPluginResolution = false)
  private lateinit var jdkFixture: MavenProjectJDKTestFixture

  @BeforeEach
  fun setUp() {
    jdkFixture = MavenProjectJDKTestFixture(maven.project, "MavenSurefireReportIntegrationTestJDK")
    runInEdtAndWait {
      WriteAction.runAndWait<RuntimeException> { jdkFixture.setUp() }
    }
  }

  @AfterEach
  fun tearDown() {
    runInEdtAndWait {
      WriteAction.runAndWait<RuntimeException> { jdkFixture.tearDown() }
    }
  }

  @Test
  fun `passing and failing tests produce correct TC message sequence`() = runBlocking {
    maven.createProjectPom("""
      <groupId>probe</groupId>
      <artifactId>probe</artifactId>
      <version>1</version>
      <properties>
        <maven.compiler.source>8</maven.compiler.source>
        <maven.compiler.target>8</maven.compiler.target>
      </properties>
      <dependencies>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
          <version>4.0</version>
          <scope>test</scope>
        </dependency>
      </dependencies>
    """.trimIndent())
    maven.repositoryPath = maven.projectPath.resolve("maven-repository")
    maven.importProjectAsync()

    maven.createProjectSubFile("src/test/java/probe/MixedTest.java", """
      package probe;
      import org.junit.Test;
      public class MixedTest {
        @Test public void passes() {}
        @Test public void fails() { throw new AssertionError("expected 1 but was 2"); }
      }
    """.trimIndent())

    // Maven exits with failure (one test fails) but still writes Surefire XML reports.
    maven.execute(MavenRunnerParameters(
      true, maven.projectPath.toString(), "pom.xml",
      mutableListOf("test"), emptyList()
    ))

    val msgs = SurefireReportParser.collectMessages(maven.projectPath)

    // Suite lifecycle
    val suiteStart = msgs.indexOfFirst { "testSuiteStarted" in it && "probe.MixedTest" in it }
    val suiteEnd   = msgs.indexOfFirst { "testSuiteFinished" in it && "probe.MixedTest" in it }
    assertTrue(suiteStart >= 0, "testSuiteStarted missing for probe.MixedTest")
    assertTrue(suiteEnd   >= 0, "testSuiteFinished missing for probe.MixedTest")
    assertTrue(suiteStart < suiteEnd, "testSuiteStarted must precede testSuiteFinished")

    // Location hints
    assertTrue(msgs.any { "locationHint='java:suite://probe.MixedTest'" in it })
    assertTrue(msgs.any { "locationHint='java:test://probe.MixedTest/passes'" in it })
    assertTrue(msgs.any { "locationHint='java:test://probe.MixedTest/fails'" in it })

    // Passing test
    assertTrue(msgs.any { "testStarted"  in it && "name='passes'" in it })
    assertTrue(msgs.any { "testFinished" in it && "name='passes'" in it })
    assertFalse(msgs.any { "testFailed"  in it && "name='passes'" in it })

    // Failing test: testFailed with message, not an error
    assertTrue(msgs.any { "testStarted" in it && "name='fails'" in it })
    val failed = msgs.first { "testFailed" in it && "name='fails'" in it }
    assertTrue("expected 1 but was 2" in failed || "AssertionError" in failed,
               "failure message missing in: $failed")
    assertFalse("error='true'" in failed, "assertion failure must not have error='true'")
    assertTrue(msgs.any { "testFinished" in it && "name='fails'" in it })
  }
}
