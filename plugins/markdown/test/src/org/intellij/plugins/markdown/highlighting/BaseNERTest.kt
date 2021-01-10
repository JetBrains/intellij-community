// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.highlighting

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.highlighting.ner.Entities
import org.intellij.plugins.markdown.highlighting.ner.MarkdownNamedEntitiesExternalAnnotator

open class BaseNERTest : BasePlatformTestCase() {
  fun doTestNER(getEntityRanges: Entities.() -> Set<TextRange>, getExpectedRanges: (String) -> Collection<IntRange>) {
    val file = myFixture.configureByFile(getTestName(true) + ".md")

    val annotator = MarkdownNamedEntitiesExternalAnnotator()
    val annotationResult = annotator.doAnnotate(annotator.collectInformation(file))
    if (annotationResult == null) {
      assertTrue("Failed to annotate", false)
      return // this line is never reached, need for smart cast of annotationResult
    }

    val expected = getExpectedRanges(file.text).map { "<${file.text.slice(it)}>" }
    val actual = annotationResult.entities.getEntityRanges().map { "<${it.substring(file.text)}>" }
    assertEquals(expected, actual)
  }

  fun doTestNER(getEntityRanges: Entities.() -> Set<TextRange>, vararg expectedRanges: IntRange) =
    doTestNER(getEntityRanges) { expectedRanges.asList() }
}