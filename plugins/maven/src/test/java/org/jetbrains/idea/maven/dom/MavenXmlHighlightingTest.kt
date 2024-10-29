// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper
import org.jetbrains.idea.maven.server.MavenDistributionsCache
import java.io.File

class MavenXmlHighlightingTest : DaemonAnalyzerTestCase() {
  override fun setUp() {
    super.setUp()
    MavenDistributionsCache.resolveEmbeddedMavenHome()
  }

  @Throws(Exception::class)
  fun testMavenValidation() {
    val file = File(MavenCustomRepositoryHelper.getOriginalTestDataPath()).resolve("MavenValidation.xml")
    doTest(LocalFileSystem.getInstance().findFileByIoFile(file)!!, false, false)
  }

}