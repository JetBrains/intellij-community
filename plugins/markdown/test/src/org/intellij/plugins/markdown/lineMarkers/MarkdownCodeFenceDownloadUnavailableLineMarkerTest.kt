// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lineMarkers

import com.intellij.codeInsight.daemon.LineMarkerInfo

/**
 * Sets up empty temp directory for downloaded external files, so all extensions
 * will have isAvailable == false.
 */
class MarkdownCodeFenceDownloadUnavailableLineMarkerTest: MarkdownCodeFenceDownloadLineMarkersTestCase() {
  override fun doTest(expectedCount: Int, predicate: (LineMarkerInfo<*>) -> Boolean) {
    withRemappedBaseDirectory(tempDirFixture.tempDirPath) {
      super.doTest(expectedCount, predicate)
    }
  }
}
