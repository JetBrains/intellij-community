// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")
package org.jetbrains.idea.maven.fixtures

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.indices.MavenSystemIndicesManager
import org.junit.Assert.assertNotNull
import org.junit.ComparisonFailure

// Highlighting checks. [MavenDomTestFixture.Highlight] is the expected-highlight matcher.

suspend fun MavenDomTestFixture.checkHighlighting() {
  checkHighlighting(projectPom)
}

suspend fun MavenDomTestFixture.checkHighlighting(f: VirtualFile) {
  if (null != indices) {
    MavenSystemIndicesManager.getInstance().waitAllGavsUpdatesCompleted()
  }
  configTest(f)
  try {
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        fixture.testHighlighting(true, false, true, f)
      }
    }
  }
  catch (e: ComparisonFailure) {
    throw e
  }
  catch (throwable: Throwable) {
    throw RuntimeException(throwable)
  }
}

suspend fun MavenDomTestFixture.checkHighlighting(file: VirtualFile, vararg expectedHighlights: MavenDomTestFixture.Highlight) {
  assertHighlighting(doHighlighting(file), *expectedHighlights)
}

suspend fun MavenDomTestFixture.doHighlighting(file: VirtualFile): Collection<HighlightInfo> {
  return withContext(Dispatchers.EDT) {
    writeIntentReadAction {
      refreshFiles(listOf(file))
      fixture.openFileInEditor(file)
      fixture.doHighlighting()
    }
  }
}

fun MavenDomTestFixture.assertHighlighting(highlightingInfos: Collection<HighlightInfo>, vararg expectedHighlights: MavenDomTestFixture.Highlight) {
  expectedHighlights.forEach { expected ->
    assertNotNull("Not highlighted: $expected", highlightingInfos.firstOrNull { expected.matches(it) })
  }
}
