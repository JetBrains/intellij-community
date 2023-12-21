// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.util.io.DigestUtil.sha1
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper
import java.io.File
import java.io.PrintWriter
import java.nio.charset.StandardCharsets

abstract class ArtifactsDownloadingTestCase : MavenMultiVersionImportingTestCase() {
  
  override fun setUp() {
    super.setUp()
    val helper = MavenCustomRepositoryHelper(dir, "plugins", "local1")
    helper.copy("plugins", "local1")
    repositoryPath = helper.getTestDataPath("local1")
  }

  protected fun createDummyArtifact(remoteRepo: String, name: String) {
    createEmptyJar(remoteRepo, name)
  }

  companion object {
    @JvmStatic
    fun createEmptyJar(dir: String, name: String) {
      val jar = File(dir, name)
      FileUtil.ensureExists(jar.getParentFile())
      IoTestUtil.createTestJar(jar)

      val digest = sha1()
      digest.update(FileUtil.loadFileBytes(jar))
      val sha1 = digest.digest()

      PrintWriter(File(dir, "$name.sha1"), StandardCharsets.UTF_8).use { out ->
        for (b in sha1) out.printf("%02x", b)
        out.println("  $name")
      }
    }
  }
}
