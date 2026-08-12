package com.intellij.ide.starter

import com.intellij.ide.starter.utils.formatArtifactName
import com.intellij.ide.starter.utils.truncateWithStableHash
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

private const val MAX_ARTIFACT_NAME_LENGTH_IN_BYTES = 240

class ArtifactNameTest {
  @Test
  fun `short names stay unchanged`() {
    "logs-short-test".truncateWithStableHash(MAX_ARTIFACT_NAME_LENGTH_IN_BYTES) shouldBe "logs-short-test"
  }

  @Test
  fun `long artifact names leave room for the TeamCity suffix and zip extension`() {
    val testName = "testQualityGateFailureConditions-1-failure-conditions-severity-thresholds-severity-thresholds-any-null-" +
                   "critical-null-high-null-moderate-null-low-null-info-null-test-coverage-thresholds-test-coverage-thresholds-" +
                   "total-51-fresh-90-qodana"

    val artifactName = formatArtifactName("logs", testName)

    artifactName.toByteArray(Charsets.UTF_8).size shouldBe MAX_ARTIFACT_NAME_LENGTH_IN_BYTES
    "$artifactName-2147483647.zip".toByteArray(Charsets.UTF_8).size shouldBe 255
    artifactName.substringAfterLast('-').matches(Regex("[0-9a-f]{12}")) shouldBe true
  }

  @Test
  fun `the hash is stable and distinguishes names with the same prefix`() {
    val commonPrefix = "a".repeat(300)
    val first = "$commonPrefix-first".truncateWithStableHash(MAX_ARTIFACT_NAME_LENGTH_IN_BYTES)
    val second = "$commonPrefix-second".truncateWithStableHash(MAX_ARTIFACT_NAME_LENGTH_IN_BYTES)

    "$commonPrefix-first".truncateWithStableHash(MAX_ARTIFACT_NAME_LENGTH_IN_BYTES) shouldBe first
    (first == second) shouldBe false
  }

  @Test
  fun `truncation does not split a Unicode code point`() {
    val truncated = "😀".repeat(10).truncateWithStableHash(18)

    truncated.substringBeforeLast('-') shouldBe "😀"
    truncated.toByteArray(Charsets.UTF_8).size shouldBe 17
  }
}
