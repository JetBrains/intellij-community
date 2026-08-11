// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.webview.preview

import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import java.nio.file.Files

internal class MarkdownPreviewPathLinkResolverTest : BasePlatformTestCase() {
  fun `test resolves relative paths from markdown file directory`() = runBlocking {
    val resolver = MarkdownPreviewPathLinkResolver(project, this)
    val source = myFixture.addFileToProject("docs/readme.md", "").virtualFile
    val target = myFixture.addFileToProject("docs/src/Main.kt", "fun main() {}\n").virtualFile

    val targets = resolver.resolve("src/Main.kt", source)

    assertEquals(listOf(target.path), targets.map { it.file.path })
    assertEquals("docs/src/Main.kt", targets.single().displayPath)
  }

  fun `test resolves project suffix paths`() = runBlocking {
    val resolver = MarkdownPreviewPathLinkResolver(project, this)
    val source = myFixture.addFileToProject("docs/readme.md", "").virtualFile
    val target = myFixture.addFileToProject("module/src/nested/Target.kt", "class Target\n").virtualFile
    IndexingTestUtil.waitUntilIndexesAreReady(project)

    val targets = resolver.resolve("nested/Target.kt", source)

    assertEquals(listOf(target.path), targets.map { it.file.path })
  }

  fun `test handles hash line and colon column locations`() = runBlocking {
    val resolver = MarkdownPreviewPathLinkResolver(project, this)
    val source = myFixture.addFileToProject("docs/readme.md", "").virtualFile
    myFixture.addFileToProject("docs/src/Main.kt", "fun first() {}\nfun second() {}\n")

    val hashLineTarget = resolver.resolve("src/Main.kt#L2", source).single()
    val colonTarget = resolver.resolve("src/Main.kt:2:3", source).single()
    val windowsSeparatorTarget = resolver.resolve("src\\Main.kt:2:3", source).single()

    assertEquals(2, hashLineTarget.line)
    assertNull(hashLineTarget.column)
    assertEquals(2, colonTarget.line)
    assertEquals(3, colonTarget.column)
    assertEquals(2, windowsSeparatorTarget.line)
    assertEquals(3, windowsSeparatorTarget.column)
    assertEquals("docs/src/Main.kt", windowsSeparatorTarget.displayPath)
  }

  fun `test ignores missing and out of project paths`() = runBlocking {
    val resolver = MarkdownPreviewPathLinkResolver(project, this)
    val source = myFixture.addFileToProject("docs/readme.md", "").virtualFile
    val outside = Files.createTempFile("markdown-preview-path-link", ".kt")
    Files.writeString(outside, "class Outside\n")

    assertEmpty(resolver.resolve("missing/Nope.kt", source))
    assertEmpty(resolver.resolve(outside.toString(), source))
  }
}
