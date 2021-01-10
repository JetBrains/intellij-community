// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.highlighting

import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.highlighting.ner.Entities

class MarkdownDateHighlightingTest : BaseNERTest() {
  override fun getTestDataPath() = MarkdownTestingUtil.TEST_DATA_PATH + "/highlighting/dates/"

  fun testFormats() = doTestNER(Entities::dates, 0..6, 8..14, 16..23, 25..32, 34..42, 44..53, 55..64, 66..75, 77..86, 88..102, 104..120)

  fun testInContext() = doTestNER(Entities::dates, 1..10, 14..23)

  fun testWords() = doTestNER(Entities::dates, 0..4, 9..27, 30..38, 44..63, 85..102, 122..130, 133..140, 145..167, 196..215)
}