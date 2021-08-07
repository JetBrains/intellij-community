// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lineMarkers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.openapi.util.io.FileUtil
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.extensions.MarkdownExtensionWithExternalFiles

class MarkdownCodeFenceDownloadAvailableLineMarkersTest: MarkdownCodeFenceDownloadLineMarkersTestCase() {
  override fun doTest(expectedCount: Int, predicate: (LineMarkerInfo<*>) -> Boolean) {
    withRemappedBaseDirectory(tempDirFixture.tempDirPath) {
      tempDirFixture.copyAll(
        FileUtil.join(MarkdownTestingUtil.TEST_DATA_PATH, "html", "plantuml", "download"),
        MarkdownExtensionWithExternalFiles.downloadCacheDirectoryName
      )
      tempDirFixture.copyAll(
        FileUtil.join(MarkdownTestingUtil.TEST_DATA_PATH, "html", "mermaid"),
        MarkdownExtensionWithExternalFiles.downloadCacheDirectoryName
      )
      super.doTest(0, predicate)
    }
  }
}
