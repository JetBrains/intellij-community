// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices

import com.intellij.maven.testFramework.MavenTestCase
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.util.progress.rawProgressReporter
import com.intellij.platform.util.progress.withRawProgressReporter
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper
import org.jetbrains.idea.maven.model.MavenRepositoryInfo
import org.jetbrains.idea.maven.model.RepositoryKind

class MavenGavIndicesTest : MavenTestCase() {

  fun testUpdateGavIndex() = runBlocking {
    val helper = MavenCustomRepositoryHelper(dir, "local1")
    val path = helper.getTestDataPath("local1")

    val gavIndex = MavenLocalGavIndexImpl(MavenRepositoryInfo("local", path, RepositoryKind.LOCAL))
    gavIndex.update(mavenProgressIndicator!!, false)
    assertSameElements(gavIndex.groupIds, "asm", "commons-io", "junit", "org.deptest", "org.example", "org.intellijgroup", "org.ow2.asm")
    assertSameElements(gavIndex.getArtifactIds("asm"), "asm", "asm-attrs")
    assertSameElements(gavIndex.getArtifactIds("org.intellijgroup"), "intellijartifact", "intellijartifactanother")
    assertSameElements(gavIndex.getVersions("junit", "junit"), "3.8.1", "3.8.2", "4.0")
    assertEmpty(gavIndex.getVersions("com.example", "some-artifact"))
    assertEmpty(gavIndex.getVersions("junit", "some-artifact"))
    assertEmpty(gavIndex.getArtifactIds("unknown"))
  }
}