// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.service

import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownLinkNavigationResolverTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test a link with an anchor resolves to the file, the project and the header`(): Unit = timeoutRunBlocking {
    myFixture.addFileToProject("docs/other.md", "# Other Heading\n")
    val source = myFixture.addFileToProject("index.md", "[link](docs/other.md#other-heading)\n")

    val data = MarkdownLinkNavigationResolver.resolve(
      link = "docs/other.md#other-heading",
      document = source.virtualFile,
      collectHeaders = MarkdownLinkNavigationResolver::collectHeadersFromPsi,
    )

    assertTrue(data.uri.endsWith("/docs/other.md#other-heading"), "uri was ${data.uri}")
    assertNotNull(data.virtualFileId)
    assertNotNull(data.projectId)
    assertEquals(listOf("# Other Heading"), data.headers?.map { it.headerText })
  }

  @Test
  fun `test an encoded anchor finds its header and stays encoded in the uri`(): Unit = timeoutRunBlocking {
    myFixture.addFileToProject("docs/other.md", "# тест\n")
    val source = myFixture.addFileToProject("index.md", "[link](docs/other.md#%D1%82%D0%B5%D1%81%D1%82)\n")

    val data = MarkdownLinkNavigationResolver.resolve(
      link = "docs/other.md#%D1%82%D0%B5%D1%81%D1%82",
      document = source.virtualFile,
      collectHeaders = MarkdownLinkNavigationResolver::collectHeadersFromPsi,
    )

    assertTrue(data.uri.endsWith("/docs/other.md#%D1%82%D0%B5%D1%81%D1%82"), "uri was ${data.uri}")
    assertEquals(listOf("# тест"), data.headers?.map { it.headerText })
  }

  @Test
  fun `test an anchor is decoded only once`(): Unit = timeoutRunBlocking {
    myFixture.addFileToProject("docs/other.md", "# тест\n")
    val source = myFixture.addFileToProject("index.md", "[link](docs/other.md#%25D1%2582%25D0%25B5%25D1%2581%25D1%2582)\n")

    val data = MarkdownLinkNavigationResolver.resolve(
      link = "docs/other.md#%25D1%2582%25D0%25B5%25D1%2581%25D1%2582",
      document = source.virtualFile,
      collectHeaders = MarkdownLinkNavigationResolver::collectHeadersFromPsi,
    )

    assertEquals(emptyList<String>(), data.headers?.map { it.headerText })
  }

  @Test
  fun `test a link without an anchor has no headers`(): Unit = timeoutRunBlocking {
    myFixture.addFileToProject("docs/other.md", "# Other Heading\n")
    val source = myFixture.addFileToProject("index.md", "[link](docs/other.md)\n")

    val data = MarkdownLinkNavigationResolver.resolve("docs/other.md", source.virtualFile, MarkdownLinkNavigationResolver::collectHeadersFromPsi)

    assertTrue(data.uri.endsWith("/docs/other.md"), "uri was ${data.uri}")
    assertNotNull(data.virtualFileId)
    assertNull(data.headers)
  }

  @Test
  fun `test a link to a missing file keeps the link as its uri`(): Unit = timeoutRunBlocking {
    val source = myFixture.addFileToProject("index.md", "[link](missing.md)\n")

    val data = MarkdownLinkNavigationResolver.resolve("missing.md", source.virtualFile, MarkdownLinkNavigationResolver::collectHeadersFromPsi)

    assertEquals("missing.md", data.uri)
    assertNull(data.virtualFileId)
    assertNull(data.projectId)
    assertNull(data.headers)
  }
}
