// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.highlighting

import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.highlighting.ner.Entities

class MarkdownOrganizationHighlightingTest : BaseNERTest() {
  override fun getTestDataPath() = MarkdownTestingUtil.TEST_DATA_PATH + "/highlighting/organizations/"

  fun testReal() = doTestNER(Entities::organizations, 0..8, 10..15, 18..27, 30..33,
                             35..40, 43..55, 57..83, 86..100, 103..104, 106..118, 124..128)
}