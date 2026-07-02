package com.intellij.ide.starter

import com.intellij.ide.starter.utils.hyphenateTestName
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TestNameExtensionTest {
  @Test
  fun `hyphenate test name handles camel case`() {
    "testMethodName".hyphenateTestName() shouldBe "test-method-name"
    "SlowFileOpeningTest".hyphenateTestName() shouldBe "slow-file-opening-test"
  }

  @Test
  fun `hyphenate test name keeps acronym prefixes as words`() {
    "SPLIT-SlowFileOpeningTest".hyphenateTestName() shouldBe "split-slow-file-opening-test"
    "SPLITSlowFileOpeningTest".hyphenateTestName() shouldBe "split-slow-file-opening-test"
    "HTTPServerTest".hyphenateTestName() shouldBe "http-server-test"
  }

  @Test
  fun `hyphenate test name replaces spaces with hyphens`() {
    "slow file opening test".hyphenateTestName() shouldBe "slow-file-opening-test"
    "slow  file   opening test".hyphenateTestName() shouldBe "slow-file-opening-test"
    "  slow file opening test  ".hyphenateTestName() shouldBe "slow-file-opening-test"
  }

  @Test
  fun `hyphenate test name replaces punctuation with hyphens`() {
    "slow:file(opening), test!".hyphenateTestName() shouldBe "slow-file-opening-test"
    "slow_file[opening] test".hyphenateTestName() shouldBe "slow-file-opening-test"
  }

  @Test
  fun `hyphenate test name preserves path segments`() {
    "SlowFileOpeningTest/fileOpeningWithDelay".hyphenateTestName() shouldBe "slow-file-opening-test/file-opening-with-delay"
  }
}
