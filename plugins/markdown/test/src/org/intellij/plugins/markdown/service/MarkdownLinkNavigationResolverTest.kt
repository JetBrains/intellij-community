// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.service

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MarkdownLinkNavigationResolverTest : BasePlatformTestCase() {
  fun `test a link with an anchor resolves to the file, the project and the header`() = timeoutRunBlocking {
    myFixture.addFileToProject("docs/other.md", "# Other Heading\n")
    val source = myFixture.addFileToProject("index.md", "[link](docs/other.md#other-heading)\n")

    val data = MarkdownLinkNavigationResolver.resolve(
      link = "docs/other.md#other-heading",
      document = source.virtualFile,
      collectHeaders = MarkdownLinkNavigationResolver::collectHeadersFromPsi,
    )

    assertTrue("uri was ${data.uri}", data.uri.endsWith("/docs/other.md#other-heading"))
    assertNotNull(data.virtualFileId)
    assertNotNull(data.projectId)
    assertEquals(listOf("# Other Heading"), data.headers?.map { it.headerText })
  }

  fun `test a link without an anchor has no headers`() = timeoutRunBlocking {
    myFixture.addFileToProject("docs/other.md", "# Other Heading\n")
    val source = myFixture.addFileToProject("index.md", "[link](docs/other.md)\n")

    val data = MarkdownLinkNavigationResolver.resolve("docs/other.md", source.virtualFile, MarkdownLinkNavigationResolver::collectHeadersFromPsi)

    assertTrue("uri was ${data.uri}", data.uri.endsWith("/docs/other.md"))
    assertNotNull(data.virtualFileId)
    assertNull(data.headers)
  }

  fun `test a link to a missing file keeps the link as its uri`() = timeoutRunBlocking {
    val source = myFixture.addFileToProject("index.md", "[link](missing.md)\n")

    val data = MarkdownLinkNavigationResolver.resolve("missing.md", source.virtualFile, MarkdownLinkNavigationResolver::collectHeadersFromPsi)

    assertEquals("missing.md", data.uri)
    assertNull(data.virtualFileId)
    assertNull(data.projectId)
    assertNull(data.headers)
  }
}
