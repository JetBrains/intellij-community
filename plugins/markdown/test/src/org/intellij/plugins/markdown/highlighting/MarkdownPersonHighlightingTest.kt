// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.highlighting

import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.highlighting.ner.Entities

class MarkdownPersonHighlightingTest : BaseNERTest() {
  override fun getTestDataPath() = MarkdownTestingUtil.TEST_DATA_PATH + "/highlighting/persons/"

  fun testWeird() = doTestNER(Entities::persons)
}