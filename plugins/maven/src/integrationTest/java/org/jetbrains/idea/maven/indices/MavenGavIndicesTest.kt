// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices

import com.intellij.maven.testFramework.fixtures.MavenCustomRepositoryHelper
import com.intellij.maven.testFramework.fixtures.mavenFixture
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.model.MavenRepositoryInfo
import org.jetbrains.idea.maven.model.RepositoryKind
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import org.junit.jupiter.api.Test

@TestApplication
class MavenGavIndicesTest {
  private val maven by mavenFixture()

  @Test
  fun testUpdateGavIndex() = runBlocking {
    val helper = MavenCustomRepositoryHelper(maven.dir, "local1")
    val path = helper.getTestData("local1")

    val progressIndicator = MavenProgressIndicator(maven.project, EmptyProgressIndicator(ModalityState.nonModal()), null)
    val gavIndex = MavenLocalGavIndexImpl(MavenRepositoryInfo("local", path.toString(), RepositoryKind.LOCAL))
    gavIndex.update(progressIndicator, false)
    assertSameElements(gavIndex.groupIds, "asm", "commons-io", "junit", "org.deptest", "org.example", "org.intellijgroup", "org.ow2.asm")
    assertSameElements(gavIndex.getArtifactIds("asm"), "asm", "asm-attrs")
    assertSameElements(gavIndex.getArtifactIds("org.intellijgroup"), "intellijartifact", "intellijartifactanother")
    assertSameElements(gavIndex.getVersions("junit", "junit"), "3.8.1", "3.8.2", "4.0")
    assertEmpty(gavIndex.getVersions("com.example", "some-artifact"))
    assertEmpty(gavIndex.getVersions("junit", "some-artifact"))
    assertEmpty(gavIndex.getArtifactIds("unknown"))
  }
}
