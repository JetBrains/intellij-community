// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class SurefireReportParserTest {

  @TempDir
  lateinit var root: Path

  private fun writeReport(fileName: String, xml: String) {
    val dir = root.resolve("target/surefire-reports")
    dir.createDirectories()
    dir.resolve(fileName).writeText(xml.trimIndent())
  }

  private fun messages() = SurefireReportParser.collectMessages(root)

  // ── directory edge cases ─────────────────────────────────────────────────

  @Test
  fun `missing reports directory returns empty list`() {
    assertTrue(messages().isEmpty())
  }

  @Test
  fun `empty reports directory returns empty list`() {
    root.resolve("target/surefire-reports").createDirectories()
    assertTrue(messages().isEmpty())
  }

  @Test
  fun `non-xml files are ignored`() {
    val dir = root.resolve("target/surefire-reports").also { it.createDirectories() }
    dir.resolve("TEST-Foo.txt").writeText("not xml")
    assertTrue(messages().isEmpty())
  }

  // ── passing test ─────────────────────────────────────────────────────────

  @Test
  fun `passing test emits suite and test lifecycle events in order`() {
    writeReport("TEST-com.example.PassTest.xml", """
      <testsuite name="com.example.PassTest">
        <testcase name="myTest" classname="com.example.PassTest" time="0.123"/>
      </testsuite>
    """)

    val msgs = messages()
    val suiteStart = msgs.indexOfFirst { "testSuiteStarted" in it && "com.example.PassTest" in it }
    val testStart  = msgs.indexOfFirst { "testStarted"      in it && "com.example.PassTest.myTest" in it }
    val testEnd    = msgs.indexOfFirst { "testFinished"     in it && "com.example.PassTest.myTest" in it }
    val suiteEnd   = msgs.indexOfFirst { "testSuiteFinished" in it && "com.example.PassTest" in it }

    assertTrue(suiteStart >= 0, "testSuiteStarted missing")
    assertTrue(testStart  >= 0, "testStarted missing")
    assertTrue(testEnd    >= 0, "testFinished missing")
    assertTrue(suiteEnd   >= 0, "testSuiteFinished missing")

    assertTrue(suiteStart < testStart,  "testSuiteStarted must precede testStarted")
    assertTrue(testStart  < testEnd,    "testStarted must precede testFinished")
    assertTrue(testEnd    < suiteEnd,   "testFinished must precede testSuiteFinished")

    assertFalse(msgs.any { "testFailed" in it || "testIgnored" in it })
  }

  @Test
  fun `test duration is converted from seconds to milliseconds`() {
    writeReport("TEST-com.example.DurationTest.xml", """
      <testsuite name="com.example.DurationTest">
        <testcase name="slow" classname="com.example.DurationTest" time="1.5"/>
      </testsuite>
    """)

    val finished = messages().first { "testFinished" in it }
    assertTrue("duration='1500'" in finished, "expected duration='1500' in: $finished")
  }

  @Test
  fun `missing time attribute omits duration from testFinished`() {
    writeReport("TEST-com.example.NoDurTest.xml", """
      <testsuite name="com.example.NoDurTest">
        <testcase name="noTime" classname="com.example.NoDurTest"/>
      </testsuite>
    """)

    val finished = messages().first { "testFinished" in it }
    assertFalse("duration=" in finished, "unexpected duration in: $finished")
  }

  // ── location hints ───────────────────────────────────────────────────────

  @Test
  fun `suite and test location hints use java protocol`() {
    writeReport("TEST-com.example.LocTest.xml", """
      <testsuite name="com.example.LocTest">
        <testcase name="myMethod" classname="com.example.LocTest"/>
      </testsuite>
    """)

    val msgs = messages()
    assertTrue(msgs.any { "locationHint='java:suite://com.example.LocTest'" in it })
    assertTrue(msgs.any { "locationHint='java:test://com.example.LocTest/myMethod'" in it })
  }

  // ── failure / error / skip ───────────────────────────────────────────────

  @Test
  fun `failure element produces testFailed without error flag`() {
    writeReport("TEST-com.example.FailTest.xml", """
      <testsuite name="com.example.FailTest">
        <testcase name="fails" classname="com.example.FailTest">
          <failure message="expected 1 but was 2">AssertionError: expected 1 but was 2
  at com.example.FailTest.fails(FailTest.java:10)</failure>
        </testcase>
      </testsuite>
    """)

    val msgs = messages()
    val failed = msgs.first { "testFailed" in it }
    assertTrue("message='expected 1 but was 2'" in failed)
    assertTrue("AssertionError" in failed)
    assertFalse("error='true'" in failed)
  }

  @Test
  fun `error element produces testFailed with error flag`() {
    writeReport("TEST-com.example.ErrTest.xml", """
      <testsuite name="com.example.ErrTest">
        <testcase name="throws" classname="com.example.ErrTest">
          <error message="boom">java.lang.RuntimeException: boom</error>
        </testcase>
      </testsuite>
    """)

    val failed = messages().first { "testFailed" in it }
    assertTrue("error='true'" in failed)
  }

  @Test
  fun `skipped element with message produces testIgnored`() {
    writeReport("TEST-com.example.SkipTest.xml", """
      <testsuite name="com.example.SkipTest">
        <testcase name="skipped" classname="com.example.SkipTest">
          <skipped message="not ready"/>
        </testcase>
      </testsuite>
    """)

    val ignored = messages().first { "testIgnored" in it }
    assertTrue("message='not ready'" in ignored)
  }

  @Test
  fun `skipped element without message falls back to text content`() {
    writeReport("TEST-com.example.SkipTest2.xml", """
      <testsuite name="com.example.SkipTest2">
        <testcase name="skipped2" classname="com.example.SkipTest2">
          <skipped>assumption failed</skipped>
        </testcase>
      </testsuite>
    """)

    val ignored = messages().first { "testIgnored" in it }
    assertTrue("message='assumption failed'" in ignored)
  }

  // ── per-test stdout/stderr ────────────────────────────────────────────────

  @Test
  fun `per-test system-out and system-err produce output events`() {
    writeReport("TEST-com.example.OutTest.xml", """
      <testsuite name="com.example.OutTest">
        <testcase name="withOutput" classname="com.example.OutTest">
          <system-out>hello stdout</system-out>
          <system-err>hello stderr</system-err>
        </testcase>
      </testsuite>
    """)

    val msgs = messages()
    assertTrue(msgs.any { "testStdOut" in it && "hello stdout" in it })
    assertTrue(msgs.any { "testStdErr" in it && "hello stderr" in it })
  }

  @Test
  fun `empty per-test system-out is suppressed`() {
    writeReport("TEST-com.example.EmptyOutTest.xml", """
      <testsuite name="com.example.EmptyOutTest">
        <testcase name="noOut" classname="com.example.EmptyOutTest">
          <system-out>   </system-out>
        </testcase>
      </testsuite>
    """)

    assertFalse(messages().any { "testStdOut" in it })
  }

  // ── TC escape sequences ───────────────────────────────────────────────────

  @Test
  fun `pipe characters are escaped as double-pipe`() {
    writeReport("TEST-Esc1.xml", """
      <testsuite name="a|b">
        <testcase name="t" classname="a|b"/>
      </testsuite>
    """)

    assertTrue(messages().any { "a||b" in it })
  }

  @Test
  fun `single quotes are escaped`() {
    writeReport("TEST-Esc2.xml", """
      <testsuite name="suite">
        <testcase name="it's a test" classname="suite">
          <failure message="expected 'foo'">details</failure>
        </testcase>
      </testsuite>
    """)

    val failed = messages().first { "testFailed" in it }
    assertTrue("expected |'foo|'" in failed)
  }

  @Test
  fun `square brackets are escaped`() {
    // Parameterized invocation: display name = "suite.param.[0]" → escaped = "suite.param.|[0|]"
    writeReport("TEST-Esc3.xml", """
      <testsuite name="suite">
        <testcase name="param[0]" classname="suite"/>
      </testsuite>
    """)

    assertTrue(messages().any { "param.|[0|]" in it })
  }

  @Test
  fun `newlines in failure details are escaped`() {
    writeReport("TEST-Esc4.xml", """
      <testsuite name="suite">
        <testcase name="multiLine" classname="suite">
          <failure message="oops">line1
line2</failure>
        </testcase>
      </testsuite>
    """)

    val failed = messages().first { "testFailed" in it }
    assertTrue("line1|nline2" in failed)
  }

  // ── classname fallback ────────────────────────────────────────────────────

  @Test
  fun `missing classname falls back to suite name`() {
    writeReport("TEST-com.example.FallbackTest.xml", """
      <testsuite name="com.example.FallbackTest">
        <testcase name="noClass"/>
      </testsuite>
    """)

    assertTrue(messages().any { "testStarted" in it && "com.example.FallbackTest.noClass" in it })
  }

  // ── parameterized test grouping ───────────────────────────────────────────

  @Test
  fun `parameterized invocations are grouped under a method suite`() {
    writeReport("TEST-com.example.DiscountTest.xml", """
      <testsuite name="com.example.DiscountTest">
        <testcase name="calculateDiscount(int, int, int)[1] 100, 10, 90" classname="com.example.DiscountTest"/>
        <testcase name="calculateDiscount(int, int, int)[2] 200, 20, 160" classname="com.example.DiscountTest"/>
        <testcase name="calculateDiscount(int, int, int)[3] 50, 100, 0" classname="com.example.DiscountTest"/>
      </testsuite>
    """)

    val msgs = messages()

    // Method-level suite wraps the invocations.
    val methodSuiteStart = msgs.indexOfFirst { "testSuiteStarted" in it && "calculateDiscount(int, int, int)" in it }
    val methodSuiteEnd = msgs.indexOfFirst { "testSuiteFinished" in it && "calculateDiscount(int, int, int)" in it }
    assertTrue(methodSuiteStart >= 0, "method suite start missing")
    assertTrue(methodSuiteEnd >= 0, "method suite end missing")
    assertTrue(methodSuiteStart < methodSuiteEnd)

    // Each invocation is a test node with the invocation suffix after a dot.
    assertTrue(msgs.any { "testStarted" in it && "calculateDiscount(int, int, int).|[1|]" in it })
    assertTrue(msgs.any { "testStarted" in it && "calculateDiscount(int, int, int).|[2|]" in it })
    assertTrue(msgs.any { "testStarted" in it && "calculateDiscount(int, int, int).|[3|]" in it })

    // The method suite is nested inside the class suite.
    val classSuiteStart = msgs.indexOfFirst { "testSuiteStarted" in it && "com.example.DiscountTest'" in it }
    assertTrue(classSuiteStart < methodSuiteStart, "class suite must open before method suite")
  }

  @Test
  fun `non-parameterized tests are not wrapped in a method suite`() {
    writeReport("TEST-com.example.PlainTest.xml", """
      <testsuite name="com.example.PlainTest">
        <testcase name="myTest" classname="com.example.PlainTest"/>
      </testsuite>
    """)

    val msgs = messages()
    // Exactly one suite: the class suite; no extra method-level suite.
    assertEquals(1, msgs.count { "testSuiteStarted" in it })
    assertTrue(msgs.any { "testStarted" in it && "com.example.PlainTest.myTest" in it })
  }

  @Test
  fun `mixed parameterized and plain tests in same suite`() {
    writeReport("TEST-com.example.MixTest.xml", """
      <testsuite name="com.example.MixTest">
        <testcase name="plain" classname="com.example.MixTest"/>
        <testcase name="param[1] a" classname="com.example.MixTest"/>
        <testcase name="param[2] b" classname="com.example.MixTest"/>
      </testsuite>
    """)

    val msgs = messages()
    assertTrue(msgs.any { "testStarted" in it && "com.example.MixTest.plain" in it })
    assertTrue(msgs.any { "testSuiteStarted" in it && "com.example.MixTest.param" in it })
    assertTrue(msgs.any { "testStarted" in it && "param.|[1|]" in it })
    assertTrue(msgs.any { "testStarted" in it && "param.|[2|]" in it })
  }

  // ── multiple suites ───────────────────────────────────────────────────────

  @Test
  fun `multiple XML files are all parsed`() {
    writeReport("TEST-com.example.A.xml", """
      <testsuite name="com.example.A">
        <testcase name="a" classname="com.example.A"/>
      </testsuite>
    """)
    writeReport("TEST-com.example.B.xml", """
      <testsuite name="com.example.B">
        <testcase name="b" classname="com.example.B"/>
      </testsuite>
    """)

    val msgs = messages()
    assertTrue(msgs.any { "com.example.A" in it && "testSuiteStarted" in it })
    assertTrue(msgs.any { "com.example.B" in it && "testSuiteStarted" in it })
  }

  @Test
  fun `files are processed in alphabetical order`() {
    writeReport("TEST-com.example.Z.xml", """
      <testsuite name="com.example.Z">
        <testcase name="z" classname="com.example.Z"/>
      </testsuite>
    """)
    writeReport("TEST-com.example.A.xml", """
      <testsuite name="com.example.A">
        <testcase name="a" classname="com.example.A"/>
      </testsuite>
    """)

    val msgs = messages()
    val idxA = msgs.indexOfFirst { "testSuiteStarted" in it && "com.example.A" in it }
    val idxZ = msgs.indexOfFirst { "testSuiteStarted" in it && "com.example.Z" in it }
    assertTrue(idxA < idxZ, "A should come before Z")
  }

  // ── reportNameSuffix stripping ────────────────────────────────────────────

  @Test
  fun `reportSuffix is stripped from suite name and classname`() {
    // Surefire appends "(suffix)" to classnames when reportNameSuffix is set.
    writeReport("TEST-com.example.SfxTest-abc.xml", """
      <testsuite name="com.example.SfxTest(abc)">
        <testcase name="myTest" classname="com.example.SfxTest(abc)"/>
      </testsuite>
    """)

    val msgs = SurefireReportParser.collectMessages(root, "abc")
    assertTrue(msgs.any { "testSuiteStarted" in it && "com.example.SfxTest'" in it },
               "suite name must not contain the suffix tag")
    assertTrue(msgs.any { "testStarted" in it && "com.example.SfxTest.myTest" in it },
               "test display name must not contain the suffix tag")
    assertFalse(msgs.any { "(abc)" in it }, "suffix tag must not appear in any message")
  }

  // ── malformed XML resilience ──────────────────────────────────────────────

  @Test
  fun `malformed XML file is skipped and other suites are still parsed`() {
    writeReport("TEST-com.example.Good.xml", """
      <testsuite name="com.example.Good">
        <testcase name="good" classname="com.example.Good"/>
      </testsuite>
    """)
    val dir = root.resolve("target/surefire-reports")
    dir.resolve("TEST-com.example.Bad.xml").writeText("<not-valid-xml")

    val msgs = messages()
    assertTrue(msgs.any { "com.example.Good" in it })
  }
}
