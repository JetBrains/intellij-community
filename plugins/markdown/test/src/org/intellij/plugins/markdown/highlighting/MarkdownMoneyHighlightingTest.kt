// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.highlighting

import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.highlighting.ner.Entities

class MarkdownMoneyHighlightingTest : BaseNERTest() {
  override fun getTestDataPath() = MarkdownTestingUtil.TEST_DATA_PATH + "/highlighting/money/"

  fun testFormats() = doTestNER(Entities::money) { file ->
    Regex("""(\S ?)*\S""")
      .findAll(file)
      .map(MatchResult::range)
      .toList()
  }

  fun testWords() = doTestNER(Entities::money, 0..18, 20..35, 37..51, 53..69, 71..88, 90..98)

  fun testWeird() = doTestNER(Entities::money, 0..2)
}