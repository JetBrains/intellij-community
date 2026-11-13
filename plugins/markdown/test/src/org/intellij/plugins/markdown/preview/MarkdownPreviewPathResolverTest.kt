// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.preview

import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.intellij.plugins.markdown.ui.preview.MarkdownImageResourceProvider
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewPathResolver
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewPathResolver.Resolution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
class MarkdownPreviewPathResolverTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @TempDir
  lateinit var tempDir: Path

  private lateinit var document: VirtualFile
  private lateinit var nearImage: VirtualFile
  private lateinit var farImage: VirtualFile

  @BeforeEach
  fun setUp() {
    nearImage = createFile("subdir/img/near.png")
    farImage = createFile("img/far.png")
    document = createFile("subdir/subdir.md")
  }

  @Test
  fun `test relative to the document`() {
    assertFound(nearImage, "img/near.png")
  }

  /** A diff shows a revision in a light file without a parent. The light file keeps the path of the revision. */
  @Test
  fun `test relative to the path of a light file`() {
    val revision = object : LightVirtualFile(document.name) {
      override fun getPath(): String = document.path
    }
    val resolution = runBlocking { MarkdownPreviewPathResolver.resolve(revision, projectRoot(), "img/near.png") }
    assertEquals(Resolution.Found(nearImage), resolution)
  }

  @Test
  fun `test relative with a dot prefix`() {
    assertFound(nearImage, "./img/near.png")
  }

  @Test
  fun `test relative above the document`() {
    assertFound(farImage, "../img/far.png")
  }

  @Test
  fun `test the document wins over the project root`() {
    val near = createFile("subdir/ambiguous.png")
    createFile("ambiguous.png")
    assertFound(near, "ambiguous.png")
  }

  @Test
  fun `test relative to the project root as a fallback`() {
    assertFound(nearImage, "subdir/img/near.png")
  }

  @Test
  fun `test a leading slash means the project root`() {
    assertFound(farImage, "/img/far.png")
  }

  @Test
  fun `test an absolute path inside the project root`() {
    assertFound(nearImage, nearImage.path)
  }

  @Test
  fun `test a file URL`() {
    // `toUri` gives `file:///C:/…` on Windows and `file:///…` elsewhere.
    assertFound(nearImage, nearImage.toNioPath().toUri().toString())
  }

  /** A link can arrive without one: the preview knows no containing file for it. */
  @Test
  fun `test a file URL resolves without a document`() {
    val resolution = runBlocking {
      MarkdownPreviewPathResolver.resolve(null, projectRoot(), nearImage.url, allowOutsideProjectRoot = true)
    }
    assertInstanceOf(Resolution.Found::class.java, resolution)
    assertEquals(nearImage, (resolution as Resolution.Found).file)
  }

  @Test
  fun `test a query string is ignored`() {
    assertFound(nearImage, "img/near.png?v=1")
  }

  @Test
  fun `test a fragment is ignored`() {
    assertFound(nearImage, "img/near.png#anchor")
  }

  @Test
  fun `test an encoded space`() {
    val image = createFile("subdir/img/spaced name.png")
    assertFound(image, "img/spaced%20name.png")
  }

  @Test
  fun `test a literal space`() {
    val image = createFile("subdir/img/spaced name.png")
    assertFound(image, "img/spaced name.png")
  }

  @Test
  fun `test a plus sign stays literal`() {
    val image = createFile("subdir/img/a+b.png")
    assertFound(image, "img/a+b.png")
  }

  @Test
  fun `test a data URI belongs to the browser`() {
    val source = "data:image/png;base64,iVBORw0KGgo="
    assertTrue(MarkdownPreviewPathResolver.isBrowserOwned(source))
    assertEquals(Resolution.NotFound, resolve(source))
  }

  @Test
  fun `test an http URL belongs to the browser`() {
    assertTrue(MarkdownPreviewPathResolver.isBrowserOwned("http://example.com/a.png"))
    assertTrue(MarkdownPreviewPathResolver.isBrowserOwned("https://example.com/a.png"))
    assertTrue(MarkdownPreviewPathResolver.isBrowserOwned("//example.com/a.png"))
  }

  @Test
  fun `test a file URL does not belong to the browser`() {
    assertFalse(MarkdownPreviewPathResolver.isBrowserOwned("file:///a.png"))
  }

  @Test
  fun `test a Windows drive letter is not a URL scheme`() {
    // `C:` has the shape of a scheme, so the image stayed broken on Windows.
    assertFalse(MarkdownPreviewPathResolver.isBrowserOwned("C:/img/a.png"))
    assertFalse(MarkdownPreviewPathResolver.isBrowserOwned("c:\\img\\a.png"))
    assertTrue(MarkdownPreviewPathResolver.isBrowserOwned("ws://example.com/a.png"))
  }

  @Test
  fun `test a file outside the project root is forbidden`() {
    val outside = createFileOutsideProject()
    assertEquals(Resolution.Forbidden, resolve(outside.path))
  }

  @Test
  fun `test a trusted project may read outside the project root`() {
    val outside = createFileOutsideProject()
    val resolution = resolve(outside.path, allowOutsideProjectRoot = true)
    assertEquals(outside, (resolution as Resolution.Found).file)
  }

  @Test
  fun `test a percent sign without an escape stays literal`() {
    val file = createFile("subdir/with%space.md")
    assertFound(file, "with%space.md")
    assertFound(file, "with%25space.md")
  }

  @Test
  fun `test a malformed path does not throw`() {
    // Reported as InvalidPathException in IJPL-96413.
    assertEquals(Resolution.NotFound, resolve("file:///C:/Users/me/proj/C:Users/me/other/a.png"))
    assertEquals(Resolution.NotFound, resolve("img/\u0000broken.png"))
    assertEquals(Resolution.NotFound, resolve("img/%ZZ.png"))
  }

  @Test
  fun `test a directory does not resolve`() {
    assertEquals(Resolution.NotFound, resolve("img"))
  }

  @Test
  fun `test a missing file does not resolve`() {
    assertEquals(Resolution.NotFound, resolve("img/absent.png"))
  }

  @Test
  fun `test an empty source does not resolve`() {
    assertEquals(Resolution.NotFound, resolve(""))
  }

  @Test
  fun `test the resource name round trip`() {
    for (source in listOf("img/near.png", "../a b.png", "/img/far.png", "file:///a.png", "img/a+b.png", "noext")) {
      val name = MarkdownImageResourceProvider.resourceName(source)
      assertTrue(name.startsWith("image/"), "`$name` must be claimed by the provider")
      assertEquals(source, MarkdownImageResourceProvider.decodeSource(name))
    }
  }

  @Test
  fun `test the resource name keeps the extension`() {
    assertTrue(MarkdownImageResourceProvider.resourceName("img/near.png").endsWith(".png"))
    assertTrue(MarkdownImageResourceProvider.resourceName("img/logo.SVG").endsWith(".SVG"))
  }

  @Test
  fun `test the path names of a source`() {
    assertEquals(setOf("img", "near.png"), MarkdownPreviewPathResolver.pathNames("./img/../img/near.png"))
    assertEquals(setOf("img", "spaced name.png"), MarkdownPreviewPathResolver.pathNames("img/spaced%20name.png?v=1#anchor"))
    assertThat(MarkdownPreviewPathResolver.pathNames("https://example.com/a.png")).isEmpty()
  }

  private fun createFile(path: String): VirtualFile = myFixture.tempDirFixture.createFile(path)

  private fun projectRoot(): VirtualFile = myFixture.tempDirFixture.getFile("")!!

  private fun createFileOutsideProject(): VirtualFile {
    val file = Files.createTempFile(tempDir, "markdown-outside", ".png").toFile()
    return StandardFileSystems.local().refreshAndFindFileByPath(file.absolutePath)!!
  }

  private fun resolve(source: String, allowOutsideProjectRoot: Boolean = false): Resolution = runBlocking {
    MarkdownPreviewPathResolver.resolve(document, projectRoot(), source, allowOutsideProjectRoot)
  }

  private fun assertFound(expected: VirtualFile, source: String) {
    val resolution = resolve(source)
    assertInstanceOf(Resolution.Found::class.java, resolution)
    assertEquals(expected, (resolution as Resolution.Found).file)
  }
}
