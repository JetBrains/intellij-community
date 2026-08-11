// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.webview.preview

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.webview.api.WebViewAssetPath
import com.intellij.ui.webview.api.WebViewAssetProviderResult
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64

internal class MarkdownPreviewResourceProviderTest : BasePlatformTestCase() {
  fun `test resolves relative resource from markdown file parent`() {
    val source = myFixture.addFileToProject("docs/readme.md", "![image](image.txt)").virtualFile
    myFixture.addFileToProject("docs/image.txt", "relative")
    val response = resolve(source, "image.txt")

    assertContent("relative", response)
  }

  fun `test resolves project-root resource`() {
    val source = myFixture.addFileToProject("docs/readme.md", "![image](/assets/image.txt)").virtualFile
    myFixture.addFileToProject("assets/image.txt", "root")
    val response = resolve(source, "/assets/image.txt")

    assertContent("root", response)
  }

  fun `test returns not found for missing resource`() {
    val source = myFixture.addFileToProject("docs/readme.md", "![image](missing.txt)").virtualFile

    val response = resolve(source, "missing.txt")

    assertTrue(response is WebViewAssetProviderResult.NotFound)
  }

  fun `test rejects file uri outside project root`() {
    val source = myFixture.addFileToProject("docs/readme.md", "").virtualFile
    val outside = Files.createTempFile("markdown-preview-resource", ".txt")
    Files.writeString(outside, "outside")

    val response = resolve(source, outside.toUri().toString())

    assertTrue(response is WebViewAssetProviderResult.Forbidden)
  }

  fun `test rejects encoded path segments`() {
    val source = myFixture.addFileToProject("docs/readme.md", "").virtualFile
    val provider = MarkdownPreviewResourceProvider(project, source)

    val response = provider.resolve(WebViewAssetPath.of("nested/${encode("image.txt")}"))

    assertTrue(response is WebViewAssetProviderResult.Forbidden)
  }

  private fun resolve(source: com.intellij.openapi.vfs.VirtualFile, rawSource: String): WebViewAssetProviderResult {
    val provider = MarkdownPreviewResourceProvider(project, source)
    return provider.resolve(WebViewAssetPath.of(encode(rawSource)))
  }

  private fun assertContent(expected: String, response: WebViewAssetProviderResult) {
    assertTrue(response is WebViewAssetProviderResult.Content)
    response as WebViewAssetProviderResult.Content
    assertEquals(expected, response.readBytes().toString(StandardCharsets.UTF_8))
  }

  private fun encode(value: String): String {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
  }
}
