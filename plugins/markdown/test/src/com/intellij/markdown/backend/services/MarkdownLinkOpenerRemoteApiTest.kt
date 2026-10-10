// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.intellij.markdown.backend.services

import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.application.smartReadAction
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import org.intellij.plugins.markdown.dto.MarkdownHeaderInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class MarkdownLinkOpenerRemoteApiTest {
  private val projectFixture = projectFixture(
    openProjectTask = createTestOpenProjectOptions(runPostStartUpActivities = false),
    openAfterCreation = true,
  )
  private val sourceRoot = projectFixture.moduleFixture("src").sourceRootFixture()
  private val targetFile = sourceRoot.psiFileFixture("target.md", "## First\n\n## тест\n\n## 日本語\n\n## Заголовок с пробелами!\n")

  @Test
  fun `an ASCII fragment finds its heading`() {
    checkHeader("first", "First")
  }

  @Test
  fun `a Cyrillic fragment finds its heading`() {
    checkHeader("тест", "тест")
  }

  @Test
  fun `an encoded Cyrillic fragment finds its heading`() {
    checkHeader("%D1%82%D0%B5%D1%81%D1%82", "тест")
  }

  @Test
  fun `an encoded Japanese fragment finds its heading`() {
    checkHeader("%E6%97%A5%E6%9C%AC%E8%AA%9E", "日本語")
  }

  @Test
  fun `an encoded fragment finds a heading with spaces and punctuation`() {
    checkHeader("%D0%B7%D0%B0%D0%B3%D0%BE%D0%BB%D0%BE%D0%B2%D0%BE%D0%BA-%D1%81-%D0%BF%D1%80%D0%BE%D0%B1%D0%B5%D0%BB%D0%B0%D0%BC%D0%B8",
                "Заголовок с пробелами!")
  }

  @Test
  fun `a fragment is decoded only once`(): Unit = timeoutRunBlocking {
    assertTrue(findHeaders("%25D1%2582%25D0%25B5%25D1%2581%25D1%2582").isEmpty())
  }

  private fun checkHeader(fragment: String, heading: String): Unit = timeoutRunBlocking {
    val header = findHeaders(fragment).single()
    assertEquals(targetFile.get().virtualFile.rpcId(), header.virtualFileId)
    assertEquals(smartReadAction(projectFixture.get()) { targetFile.get().text.indexOf("## $heading") }, header.textOffset)
  }

  private suspend fun findHeaders(fragment: String): List<MarkdownHeaderInfo> {
    val target = targetFile.get()
    smartReadAction(projectFixture.get()) { }
    val data = MarkdownLinkOpenerRemoteApiImpl().fetchLinkNavigationData("target.md#$fragment", target.virtualFile.rpcId())
    return requireNotNull(data.headers)
  }
}
