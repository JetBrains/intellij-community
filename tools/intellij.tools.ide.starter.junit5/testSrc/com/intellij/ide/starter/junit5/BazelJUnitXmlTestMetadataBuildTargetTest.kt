// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.junit5

import com.intellij.platform.testFramework.teamCity.BazelJUnitXmlTestMetadataReporter
import com.intellij.platform.testFramework.teamCity.BazelJUnitXmlTestMetadataReporter.MetadataScenario
import com.intellij.platform.testFramework.teamCity.BazelJUnitXmlTestMetadataReporter.TeamCityMetadataWorkflow
import com.intellij.platform.testFramework.teamCity.BazelJUnitXmlTestMetadataReporter.TestNameFormat
import com.intellij.platform.testFramework.teamCity.BazelJUnitXmlTestMetadataReporter.createBazelTestLogMetadataWorkflow
import com.intellij.platform.testFramework.teamCity.BazelJUnitXmlTestMetadataReporter.runBazelTestReportsWorkflow
import com.intellij.platform.testFramework.teamCity.TeamCityReporter
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
  fun `reports sibling test log metadata for every testcase in bazel testlogs`(@TempDir tempRoot: Path) {
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
    firstTestDir.resolve("test.log").writeText("first log")

    val secondTestDir = bazelTestLogs / "beta" / "test"
    secondTestDir.createDirectories()
    secondTestDir.resolve("test.xml").writeText(
      junitXml("""<testcase classname="com.example.ThirdTest" name="thirdTest"/>""")
    )
    secondTestDir.resolve("test.log").writeText("second log")

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
      junitXml("""<testcase classname="com.example.FirstTest" name="simpleTest"/>""")
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
  fun `reports metadata for custom scenario over bazel test reports`(@TempDir tempRoot: Path) {
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
        workflow = TeamCityMetadataWorkflow(
          testNameFormat = TestNameFormat.NAME,
          scenario = MetadataScenario(
            metadataName = "Custom Metadata",
            metadataType = TeamCityReporter.MetadataType.TEXT,
            metadataValue = { junitXmlPath, testCase -> "${junitXmlPath.fileName}:${testCase.className}" },
          ),
        ),
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
        ),
        BazelJUnitXmlTestMetadataReporter.JUnitXmlTestCase(
          name = "secondTest",
          className = "com.example.SecondTest",
        ),
      )
    )
    TestNameFormat.CLASSNAME_AND_NAME.format(testCases.first()).shouldBe("com.example.FirstTest.firstTest")
  }

  private fun junitXml(vararg testCases: String): String =
    """
    <testsuites>
      <testsuite>
        ${testCases.joinToString("\n        ")}
      </testsuite>
    </testsuites>
    """.trimIndent()

}
