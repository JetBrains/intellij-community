// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import java.io.File

internal object MarkdownTestingUtil {
  @JvmField
  val TEST_DATA_PATH = findTestDataPath()

  private fun findTestDataPath(): String {
    val homePath = PathManager.getHomePath()
    if (File("$homePath/community/plugins").isDirectory) {
      return FileUtil.toSystemIndependentName("$homePath/community/plugins/markdown/test/data")
    }
    val parentPath = PathUtil.getParentPath(homePath)
    val targetPath = when {
      File("$parentPath/intellij-plugins").isDirectory -> "$parentPath/intellij-plugins/markdown/test/data"
      File("$parentPath/community/plugins").isDirectory -> "$parentPath/community/plugins/markdown/test/data"
      else -> null
    }
    return targetPath?.let(FileUtil::toSystemIndependentName) ?: ""
  }
}
