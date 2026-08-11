// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.repository.search.completion

import com.intellij.repository.search.completion.lookup.DependencyCompletionFuzzyMatcher
import com.intellij.util.text.matching.MatchedFragment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DependencyCompletionFuzzyMatcherTest {

  private fun highlighted(input: String, candidate: String): List<String> =
    DependencyCompletionFuzzyMatcher(input)
      .getMatchingFragments(input, candidate)
      .map { candidate.substring(it.startOffset, it.endOffset) }

  @Test
  fun `dash separator includes the trailing dash in the matched token`() {
    assertThat(highlighted("junit-api", "junit-jupiter-api")).containsExactly("junit-", "api")
  }

  @Test
  fun `dot separator does not include the candidate dash`() {
    assertThat(highlighted("junit.api", "junit-jupiter-api")).containsExactly("junit", "api")
  }

  @Test
  fun `colon parts align structurally and prefix tokens are partially matched`() {
    assertThat(highlighted("org.springframework:spring-b", "org.springframework:spring-beans"))
      .containsExactly("org.", "springframework", "spring-", "b")
  }

  @Test
  fun `partially typed token highlights its common prefix and matching continues`() {
    // "stater" is a typo for "starter"; only the shared "sta" is highlighted, and "actuator" still matches.
    // The adjacent "boot-" and "sta" fragments render as a continuous "boot-sta" highlight.
    assertThat(highlighted("boot-stater-actuator", "spring-boot-starter-actuator"))
      .containsExactly("boot-", "sta", "actuator")
  }

  @Test
  fun `match prefers the artifact over an incidental token match in the group`() {
    // The group "org.springframework.boot" also ends with a "boot" token, but the artifact matches all
    // three typed tokens, so the whole highlight lands on the artifact, not the group.
    assertThat(highlighted("boot-stater-actuator", "org.springframework.boot:spring-boot-starter-actuator:4.1.0"))
      .containsExactly("boot-", "sta", "actuator")
  }

  @Test
  fun `prefix token absent from the candidate is skipped while the rest still matches`() {
    // The candidate has no "starter" token, so "stater" is skipped; "boot-" and "actuator" are adjacent
    // in the candidate and render as a continuous "boot-actuator" highlight.
    assertThat(highlighted("boot-stater-actuator", "spring-boot-actuator"))
      .containsExactly("boot-", "actuator")
  }

  @Test
  fun `single token skips non-matching candidate tokens`() {
    assertThat(highlighted("api", "junit-jupiter-api")).containsExactly("api")
  }

  @Test
  fun `matching is case-insensitive`() {
    assertThat(highlighted("JUnit", "junit-jupiter-api")).containsExactly("junit")
  }

  @Test
  fun `unmatched input falls back to longest common substring`() {
    // "uni" is the longest case-insensitive substring of the candidate present in the input.
    assertThat(highlighted("uniXYZ", "junit-jupiter-api")).containsExactly("uni")
  }

  @Test
  fun `completely unmatched input produces no fragments`() {
    assertThat(highlighted("zzz", "junit-jupiter-api")).isEmpty()
  }

  @Test
  fun `fragments are non-overlapping and strictly increasing`() {
    val candidate = "org.springframework:spring-boot-starter-web"
    val fragments: List<MatchedFragment> =
      DependencyCompletionFuzzyMatcher("spring:boot-web").getMatchingFragments("spring:boot-web", candidate)
    var previousEnd = -1
    for (fragment in fragments) {
      assertThat(fragment.startOffset).isGreaterThanOrEqualTo(previousEnd)
      assertThat(fragment.endOffset).isGreaterThan(fragment.startOffset)
      previousEnd = fragment.endOffset
    }
  }
}
