// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")
package org.jetbrains.idea.maven.fixtures

import com.intellij.maven.testFramework.fixtures.MavenTestFixture
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.util.io.DigestUtil.sha1
import java.io.File
import java.io.PrintWriter
import java.nio.charset.StandardCharsets

// Ported from ArtifactsDownloadingTestCase.

fun MavenTestFixture.createDummyArtifact(remoteRepo: String, name: String) {
  createEmptyJar(remoteRepo, name)
}

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
