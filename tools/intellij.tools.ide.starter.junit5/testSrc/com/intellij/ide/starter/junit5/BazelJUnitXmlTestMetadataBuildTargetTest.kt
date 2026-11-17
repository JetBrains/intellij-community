// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.junit5

import com.intellij.platform.testFramework.teamCity.TeamCityReporter
import com.intellij.platform.testFramework.teamCity.bazelReporter.BazelJUnitXmlTestMetadataReporter
import com.intellij.platform.testFramework.teamCity.bazelReporter.BazelJUnitXmlTestMetadataReporter.BazelTestReportsWorkflow
import com.intellij.platform.testFramework.teamCity.bazelReporter.BazelJUnitXmlTestMetadataReporter.TestNameFormat
import com.intellij.platform.testFramework.teamCity.bazelReporter.BazelJUnitXmlTestMetadataReporter.createBazelTestLogMetadataWorkflow
import com.intellij.platform.testFramework.teamCity.bazelReporter.BazelJUnitXmlTestMetadataReporter.createBazelTestOwnersMetadataWorkflow
import com.intellij.platform.testFramework.teamCity.bazelReporter.BazelJUnitXmlTestMetadataReporter.runBazelTestReportsWorkflow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.writeText

class BazelJUnitXmlTestMetadataBuildTargetTest {
  private fun captureStdout(block: () -> Unit): List<String> {
    val originalOut = System.out
    val captured = ByteArrayOutputStream()
    System.setOut(PrintStream(captured))
    try {
      block()
    }
    finally {
      System.out.flush()
      System.setOut(originalOut)
    }
    return captured.toString().lineSequence().filter { it.startsWith("##teamcity[") }.toList()
  }

  @Test
  fun `reports sibling test log metadata only for failed test cases in bazel testlogs`(@TempDir tempRoot: Path) {
    val execroot = tempRoot / "2bd3cb95011b89f245615c4315705f58" / "execroot" / "_main"
    val javaRunfiles = execroot / "bazel-out" / "local_linux-fastbuild" / "bin" / "build" / "report.runfiles"
    javaRunfiles.createDirectories()
    val bazelTestLogs = execroot / "bazel-out" / "local_linux-fastbuild" / "testlogs"
    val firstTestDir = bazelTestLogs / "alpha" / "test"
    firstTestDir.createDirectories()
    firstTestDir.resolve("test.xml").writeText(
      junitXml(
        """<testcase classname="com.example.FirstTest" name="successfulTest"/>""",
        """<testcase classname="com.example.FirstTest" name="simpleTest"><failure/></testcase>""",
        """<testcase classname="com.example.SecondTest" name="parameterized[1]"><error/></testcase>""",
        """<testcase classname="com.example.SecondTest" name="skippedTest"><skipped/></testcase>""",
      )
    )
    firstTestDir.resolve("test.log").writeText("first log")

    val secondTestDir = bazelTestLogs / "beta" / "test"
    secondTestDir.createDirectories()
    secondTestDir.resolve("test.xml").writeText(
      junitXml("""<testcase classname="com.example.ThirdTest" name="thirdTest"><failure/></testcase>""")
    )
    secondTestDir.resolve("test.log").writeText("second log")

    val successfulTestDir = bazelTestLogs / "gamma" / "test"
    successfulTestDir.createDirectories()
    successfulTestDir.resolve("test.xml").writeText(
      junitXml("""<testcase classname="com.example.FourthTest" name="successfulTest"/>""")
    )
    successfulTestDir.resolve("test.log").writeText("successful log")

    val lines = captureStdout {
      runBazelTestReportsWorkflow(
        javaRunfilesPath = javaRunfiles,
        workflow = createBazelTestLogMetadataWorkflow(
          reportedPathBase = "bazel.logs.zip!",
          metadataName = "Bazel log",
          testNameFormat = TestNameFormat.CLASSNAME_AND_NAME,
        ),
      )
    }

    lines.size.shouldBe(3)
    lines[0].shouldContain("##teamcity[testMetadata")
    lines[0].shouldContain("testName='com.example.FirstTest.simpleTest'")
    lines[0].shouldContain("type='artifact'")
    lines[0].shouldContain("name='Bazel log'")
    lines[0].shouldContain(
      "value='bazel.logs.zip!/2bd3cb95011b89f245615c4315705f58/execroot/_main/bazel-out/local_linux-fastbuild/testlogs/alpha/test/test.log'"
    )
    lines[1].shouldContain("testName='com.example.SecondTest.parameterized|[1|]'")
    lines[1].shouldContain(
      "value='bazel.logs.zip!/2bd3cb95011b89f245615c4315705f58/execroot/_main/bazel-out/local_linux-fastbuild/testlogs/alpha/test/test.log'"
    )
    lines[2].shouldContain("testName='com.example.ThirdTest.thirdTest'")
    lines[2].shouldContain(
      "value='bazel.logs.zip!/2bd3cb95011b89f245615c4315705f58/execroot/_main/bazel-out/local_linux-fastbuild/testlogs/beta/test/test.log'"
    )
  }

  @Test
  fun `runs bazel test log metadata workflow from command line option`(@TempDir tempRoot: Path) {
    val execroot = tempRoot / "2bd3cb95011b89f245615c4315705f58" / "execroot" / "_main"
    val javaRunfiles = execroot / "bazel-out" / "local_linux-fastbuild" / "bin" / "build" / "report.runfiles"
    javaRunfiles.createDirectories()
    val bazelTestLogs = execroot / "bazel-out" / "local_linux-fastbuild" / "testlogs"
    val testDir = bazelTestLogs / "alpha" / "test"
    testDir.createDirectories()
    testDir.resolve("test.xml").writeText(
      junitXml("""<testcase classname="com.example.FirstTest" name="simpleTest"><failure/></testcase>""")
    )

    val lines = captureStdout {
      BazelJUnitXmlTestMetadataReporter.runTool(
        args = arrayOf(
          "--workflow", "bazel-test-log-metadata",
          "--logs-base", "bazel.logs.zip!",
          "--metadata-name", "Bazel log",
        ),
        javaRunfilesPath = javaRunfiles,
      )
    }

    lines.size.shouldBe(1)
    lines[0].shouldContain("##teamcity[testMetadata")
    lines[0].shouldContain("testName='com.example.FirstTest.simpleTest'")
    lines[0].shouldContain("type='artifact'")
    lines[0].shouldContain("name='Bazel log'")
    lines[0].shouldContain(
      "value='bazel.logs.zip!/2bd3cb95011b89f245615c4315705f58/execroot/_main/bazel-out/local_linux-fastbuild/testlogs/alpha/test/test.log'"
    )
  }

  @Test
  fun `non fatal command line workflow reports warning instead of throwing`(@TempDir tempRoot: Path) {
    val execroot = tempRoot / "2bd3cb95011b89f245615c4315705f58" / "execroot" / "_main"
    val javaRunfiles = execroot / "bazel-out" / "local_linux-fastbuild" / "bin" / "build" / "report.runfiles"
    javaRunfiles.createDirectories()

    val lines = captureStdout {
      BazelJUnitXmlTestMetadataReporter.runToolNonFatal(
        args = arrayOf(
          "--workflow", "bazel-test-log-metadata",
          "--logs-base", "bazel.logs.zip!",
        ),
        { javaRunfiles },
      ).shouldBe(false)
    }

    lines.size.shouldBe(1)
    lines[0].shouldContain("##teamcity[message")
    lines[0].shouldContain("status='WARNING'")
    lines[0].shouldContain("Failed to report Bazel JUnit XML test metadata")
  }

  @Test
  fun `reports metadata for custom workflow over bazel test reports`(@TempDir tempRoot: Path) {
    val execroot = tempRoot / "2bd3cb95011b89f245615c4315705f58" / "execroot" / "_main"
    val javaRunfiles = execroot / "bazel-out" / "local_linux-fastbuild" / "bin" / "build" / "report.runfiles"
    javaRunfiles.createDirectories()
    val bazelTestLogs = execroot / "bazel-out" / "local_linux-fastbuild" / "testlogs"
    val firstTestDir = bazelTestLogs / "alpha" / "test"
    firstTestDir.createDirectories()
    firstTestDir.resolve("test.xml").writeText(
      junitXml(
        """<testcase classname="com.example.FirstTest" name="simpleTest"/>""",
        """<testcase classname="com.example.SecondTest" name="parameterized[1]"/>""",
      )
    )

    val lines = captureStdout {
      runBazelTestReportsWorkflow(
        javaRunfilesPath = javaRunfiles,
        workflow = BazelTestReportsWorkflow { testReports ->
          for (report in testReports.junitXmlReports) {
            for (testCase in report.testCases) {
              TeamCityReporter.reportTestMetadata(
                testName = TestNameFormat.NAME.format(testCase),
                value = "${report.path.fileName}:${testCase.className}",
                name = "Custom Metadata",
                type = TeamCityReporter.MetadataType.TEXT,
              )
            }
          }
        },
      )
    }

    lines.size.shouldBe(2)
    lines[0].shouldContain("##teamcity[testMetadata")
    lines[0].shouldContain("testName='simpleTest'")
    lines[0].shouldContain("type='text'")
    lines[0].shouldContain("name='Custom Metadata'")
    lines[0].shouldContain("value='test.xml:com.example.FirstTest'")
    lines[1].shouldContain("testName='parameterized|[1|]'")
    lines[1].shouldContain("value='test.xml:com.example.SecondTest'")
  }

  @Test
  fun `reports code owner metadata for test cases resolved from test class owners file`(@TempDir tempRoot: Path) {
    val execroot = tempRoot / "2bd3cb95011b89f245615c4315705f58" / "execroot" / "_main"
    val javaRunfiles = execroot / "bazel-out" / "local_linux-fastbuild" / "bin" / "build" / "report.runfiles"
    javaRunfiles.createDirectories()
    val bazelTestLogs = execroot / "bazel-out" / "local_linux-fastbuild" / "testlogs"
    val testDir = bazelTestLogs / "alpha" / "test"
    testDir.createDirectories()
    testDir.resolve("test.xml").writeText(
      junitXml(
        """<testcase classname="com.example.FirstTest" name="simpleTest"/>""",
        """<testcase classname="com.example.FirstTest${'$'}Nested" name="nestedTest"/>""",
        """<testcase classname="com.example.UnownedTest" name="unownedTest"/>""",
      )
    )
    val testClassOwners = tempRoot / "test-class-owners.ndjson"
    testClassOwners.writeText("""{"fqn":"com.example.FirstTest","owner":"First Team"}""")

    val lines = captureStdout {
      runBazelTestReportsWorkflow(
        javaRunfilesPath = javaRunfiles,
        workflow = createBazelTestOwnersMetadataWorkflow(testClassOwners = testClassOwners),
      )
    }

    lines.size.shouldBe(2)
    lines[0].shouldContain("##teamcity[testMetadata")
    lines[0].shouldContain("testName='com.example.FirstTest.simpleTest'")
    lines[0].shouldContain("type='text'")
    lines[0].shouldContain("name='Code Owner'")
    lines[0].shouldContain("value='First Team'")
    // the nested class resolves through its top-level class, the unowned class reports nothing
    lines[1].shouldContain("testName='com.example.FirstTest${'$'}Nested.nestedTest'")
    lines[1].shouldContain("type='text'")
    lines[1].shouldContain("value='First Team'")
  }

  /** TeamCity leaves out a suite that the test name starts with, and keeps another suite before the test name. */
  @Test
  fun `names each test case as TeamCity names it`(@TempDir tempRoot: Path) {
    val testCases = listOf(
      testCase(suiteName = "com.example.FirstTest", className = "com.example.FirstTest", name = "simpleTest()"),
      testCase(suiteName = "com.example.FirstTest", className = null, name = "Class Configuration"),
      testCase(suiteName = "com.example.AllTests", className = "com.example.SecondTest", name = "test"),
      testCase(suiteName = "com.example.ThirdTest", className = "com.example.ThirdTest", name = "com.example.ThirdTest.dynamic"),
      testCase(suiteName = null, className = "com.example.FourthTest", name = "test"),
    )

    testCases.map(TestNameFormat.CLASSNAME_AND_NAME::format).shouldBe(listOf(
      "com.example.FirstTest.simpleTest()",
      "com.example.FirstTest: Class Configuration",
      "com.example.AllTests: com.example.SecondTest.test",
      "com.example.ThirdTest.dynamic",
      "com.example.FourthTest.test",
    ))
  }

  @Test
  fun `reports the code owner of a class configuration failure from its suite name`(@TempDir tempRoot: Path) {
    val execroot = tempRoot / "2bd3cb95011b89f245615c4315705f58" / "execroot" / "_main"
    val javaRunfiles = execroot / "bazel-out" / "local_linux-fastbuild" / "bin" / "build" / "report.runfiles"
    javaRunfiles.createDirectories()
    val testDir = execroot / "bazel-out" / "local_linux-fastbuild" / "testlogs" / "alpha" / "test"
    testDir.createDirectories()
    testDir.resolve("test.xml").writeText(
      """
      <testsuites>
        <testsuite name="com.example.FirstTest${'$'}Nested[MONOLITH].SPLIT">
          <testcase classname="" name="Class Configuration"/>
        </testsuite>
        <testsuite name="com.example.UnownedTest">
          <testcase classname="" name="Class Configuration"/>
        </testsuite>
      </testsuites>
      """.trimIndent()
    )
    val testClassOwners = tempRoot / "test-class-owners.ndjson"
    testClassOwners.writeText("""{"fqn":"com.example.FirstTest","owner":"First Team"}""")

    val lines = captureStdout {
      runBazelTestReportsWorkflow(
        javaRunfilesPath = javaRunfiles,
        workflow = createBazelTestOwnersMetadataWorkflow(testClassOwners = testClassOwners),
      )
    }

    withClue(lines.joinToString("\n")) { lines.size.shouldBe(1) }
    lines[0].shouldContain("testName='com.example.FirstTest${'$'}Nested|[MONOLITH|].SPLIT: Class Configuration'")
    lines[0].shouldContain("value='First Team'")
  }

  @Test
  fun `warns and reports nothing when test class owners file is missing`(@TempDir tempRoot: Path) {
    val execroot = tempRoot / "2bd3cb95011b89f245615c4315705f58" / "execroot" / "_main"
    val javaRunfiles = execroot / "bazel-out" / "local_linux-fastbuild" / "bin" / "build" / "report.runfiles"
    javaRunfiles.createDirectories()
    val bazelTestLogs = execroot / "bazel-out" / "local_linux-fastbuild" / "testlogs"
    val testDir = bazelTestLogs / "alpha" / "test"
    testDir.createDirectories()
    testDir.resolve("test.xml").writeText(
      junitXml("""<testcase classname="com.example.FirstTest" name="simpleTest"/>""")
    )

    val lines = captureStdout {
      BazelJUnitXmlTestMetadataReporter.runTool(
        args = arrayOf(
          "--workflow", "bazel-test-owners-metadata",
          "--test-class-owners", (tempRoot / "missing.ndjson").toString(),
        ),
        javaRunfilesPath = javaRunfiles,
      )
    }

    lines.size.shouldBe(1)
    lines[0].shouldContain("##teamcity[message")
    lines[0].shouldContain("status='WARNING'")
    lines[0].shouldContain("skipping Code Owner test metadata")
  }

  @Test
  fun `runs multiple workflows from repeated command line workflow options`(@TempDir tempRoot: Path) {
    val execroot = tempRoot / "2bd3cb95011b89f245615c4315705f58" / "execroot" / "_main"
    val javaRunfiles = execroot / "bazel-out" / "local_linux-fastbuild" / "bin" / "build" / "report.runfiles"
    javaRunfiles.createDirectories()
    val bazelTestLogs = execroot / "bazel-out" / "local_linux-fastbuild" / "testlogs"
    val testDir = bazelTestLogs / "alpha" / "test"
    testDir.createDirectories()
    testDir.resolve("test.xml").writeText(
      junitXml(
        """<testcase classname="com.example.FirstTest" name="simpleTest"><failure/></testcase>""",
        """<testcase classname="com.example.FirstTest" name="successfulTest"/>""",
      )
    )
    val testClassOwners = tempRoot / "test-class-owners.ndjson"
    testClassOwners.writeText("""{"fqn":"com.example.FirstTest","owner":"First Team"}""")

    val lines = captureStdout {
      BazelJUnitXmlTestMetadataReporter.runTool(
        args = arrayOf(
          "--workflow", "bazel-test-log-metadata",
          "--logs-base", "bazel.logs.zip!",
          "--workflow", "bazel-test-owners-metadata",
          "--test-class-owners", testClassOwners.toString(),
        ),
        javaRunfilesPath = javaRunfiles,
      )
    }

    lines.size.shouldBe(3)
    lines[0].shouldContain("testName='com.example.FirstTest.simpleTest'")
    lines[0].shouldContain("type='artifact'")
    lines[1].shouldContain("testName='com.example.FirstTest.simpleTest'")
    lines[1].shouldContain("name='Code Owner'")
    lines[1].shouldContain("value='First Team'")
    lines[2].shouldContain("testName='com.example.FirstTest.successfulTest'")
    lines[2].shouldContain("name='Code Owner'")
    lines[2].shouldContain("value='First Team'")
  }

  @Test
  fun `resolves bazel testlogs from java runfiles path`(@TempDir tempRoot: Path) {
    val execroot = tempRoot / "2bd3cb95011b89f245615c4315705f58" / "execroot" / "_main"
    val javaRunfiles = execroot / "bazel-out" / "local_linux-fastbuild" / "bin" / "build" / "report.runfiles"
    javaRunfiles.createDirectories()
    val bazelTestLogs = execroot / "bazel-out" / "local_linux-fastbuild" / "testlogs"
    bazelTestLogs.createDirectories()

    BazelJUnitXmlTestMetadataReporter.resolveBazelTestLogsPath(
      javaRunfilesPath = javaRunfiles,
    ).shouldBe(bazelTestLogs.toRealPath())
  }

  @Test
  fun `read test cases captures parent testsuite name`(@TempDir tempRoot: Path) {
    val junitXml = tempRoot.resolve("test.xml")
    junitXml.writeText(
      """
      <testsuites>
        <testsuite name="FirstSuite">
          <testcase classname="com.example.FirstTest" name="firstTest"/>
        </testsuite>
        <testsuite name="SecondSuite">
          <testcase classname="com.example.SecondTest" name="secondTest"/>
        </testsuite>
        <testcase classname="com.example.OrphanTest" name="orphanTest"/>
      </testsuites>
      """.trimIndent()
    )

    val testCases = BazelJUnitXmlTestMetadataReporter.readTestCases(junitXml)
    testCases.shouldBe(
      listOf(
        BazelJUnitXmlTestMetadataReporter.JUnitXmlTestCase(
          name = "firstTest",
          className = "com.example.FirstTest",
          suiteName = "FirstSuite",
        ),
        BazelJUnitXmlTestMetadataReporter.JUnitXmlTestCase(
          name = "secondTest",
          className = "com.example.SecondTest",
          suiteName = "SecondSuite",
        ),
      )
    )
    TestNameFormat.CLASSNAME_AND_NAME.format(testCases.first()).shouldBe("FirstSuite: com.example.FirstTest.firstTest")
  }

  private fun testCase(suiteName: String?, className: String?, name: String) =
    BazelJUnitXmlTestMetadataReporter.JUnitXmlTestCase(name = name, className = className, suiteName = suiteName)

  private fun junitXml(vararg testCases: String): String =
    """
    <testsuites>
      <testsuite>
        ${testCases.joinToString("\n        ")}
      </testsuite>
    </testsuites>
    """.trimIndent()

}
