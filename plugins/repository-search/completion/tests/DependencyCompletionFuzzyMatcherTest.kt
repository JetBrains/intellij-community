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
