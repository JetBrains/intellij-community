// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.preview

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import kotlinx.coroutines.runBlocking
import org.intellij.plugins.markdown.ui.preview.MarkdownImagePathResolver
import org.intellij.plugins.markdown.ui.preview.MarkdownImagePathResolver.Resolution
import org.intellij.plugins.markdown.ui.preview.MarkdownImageResourceProvider

class MarkdownImagePathResolverTest : BasePlatformTestCase() {
  override fun createTempDirTestFixture(): TempDirTestFixture =
    IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture()

  private lateinit var document: VirtualFile
  private lateinit var nearImage: VirtualFile
  private lateinit var farImage: VirtualFile

  override fun setUp() {
    super.setUp()
    nearImage = createFile("subdir/img/near.png")
    farImage = createFile("img/far.png")
    document = createFile("subdir/subdir.md")
  }

  fun `test relative to the document`() {
    assertFound(nearImage, "img/near.png")
  }

  fun `test relative with a dot prefix`() {
    assertFound(nearImage, "./img/near.png")
  }

  fun `test relative above the document`() {
    assertFound(farImage, "../img/far.png")
  }

  fun `test the document wins over the project root`() {
    val near = createFile("subdir/ambiguous.png")
    createFile("ambiguous.png")
    assertFound(near, "ambiguous.png")
  }

  fun `test relative to the project root as a fallback`() {
    assertFound(nearImage, "subdir/img/near.png")
  }

  fun `test a leading slash means the project root`() {
    assertFound(farImage, "/img/far.png")
  }

  fun `test an absolute path inside the project root`() {
    assertFound(nearImage, nearImage.path)
  }

  fun `test a file URL`() {
    // `toUri` gives `file:///C:/…` on Windows and `file:///…` elsewhere.
    assertFound(nearImage, nearImage.toNioPath().toUri().toString())
  }

  fun `test a query string is ignored`() {
    assertFound(nearImage, "img/near.png?v=1")
  }

  fun `test a fragment is ignored`() {
    assertFound(nearImage, "img/near.png#anchor")
  }

  fun `test an encoded space`() {
    val image = createFile("subdir/img/spaced name.png")
    assertFound(image, "img/spaced%20name.png")
  }

  fun `test a literal space`() {
    val image = createFile("subdir/img/spaced name.png")
    assertFound(image, "img/spaced name.png")
  }

  fun `test a plus sign stays literal`() {
    val image = createFile("subdir/img/a+b.png")
    assertFound(image, "img/a+b.png")
  }

  fun `test a data URI belongs to the browser`() {
    val source = "data:image/png;base64,iVBORw0KGgo="
    assertTrue(MarkdownImagePathResolver.isBrowserOwned(source))
    assertEquals(Resolution.NotFound, resolve(source))
  }

  fun `test an http URL belongs to the browser`() {
    assertTrue(MarkdownImagePathResolver.isBrowserOwned("http://example.com/a.png"))
    assertTrue(MarkdownImagePathResolver.isBrowserOwned("https://example.com/a.png"))
    assertTrue(MarkdownImagePathResolver.isBrowserOwned("//example.com/a.png"))
  }

  fun `test a file URL does not belong to the browser`() {
    assertFalse(MarkdownImagePathResolver.isBrowserOwned("file:///a.png"))
  }

  fun `test a Windows drive letter is not a URL scheme`() {
    // `C:` has the shape of a scheme, so the image stayed broken on Windows.
    assertFalse(MarkdownImagePathResolver.isBrowserOwned("C:/img/a.png"))
    assertFalse(MarkdownImagePathResolver.isBrowserOwned("c:\\img\\a.png"))
    assertTrue(MarkdownImagePathResolver.isBrowserOwned("ws://example.com/a.png"))
  }

  fun `test a file outside the project root is forbidden`() {
    val outside = createFileOutsideProject()
    assertEquals(Resolution.Forbidden, resolve(outside.path))
  }

  fun `test a trusted project may read outside the project root`() {
    val outside = createFileOutsideProject()
    val resolution = resolve(outside.path, allowOutsideProjectRoot = true)
    assertEquals(outside, (resolution as Resolution.Found).file)
  }

  fun `test a malformed path does not throw`() {
    // Reported as InvalidPathException in IJPL-96413.
    assertEquals(Resolution.NotFound, resolve("file:///C:/Users/me/proj/C:Users/me/other/a.png"))
    assertEquals(Resolution.NotFound, resolve("img/\u0000broken.png"))
    assertEquals(Resolution.NotFound, resolve("img/%ZZ.png"))
  }

  fun `test a directory does not resolve`() {
    assertEquals(Resolution.NotFound, resolve("img"))
  }

  fun `test a missing file does not resolve`() {
    assertEquals(Resolution.NotFound, resolve("img/absent.png"))
  }

  fun `test an empty source does not resolve`() {
    assertEquals(Resolution.NotFound, resolve(""))
  }

  fun `test the resource name round trip`() {
    for (source in listOf("img/near.png", "../a b.png", "/img/far.png", "file:///a.png", "img/a+b.png", "noext")) {
      val name = MarkdownImageResourceProvider.resourceName(source)
      assertTrue("`$name` must be claimed by the provider", name.startsWith("image/"))
      assertEquals(source, MarkdownImageResourceProvider.decodeSource(name))
    }
  }

  fun `test the resource name keeps the extension`() {
    assertTrue(MarkdownImageResourceProvider.resourceName("img/near.png").endsWith(".png"))
    assertTrue(MarkdownImageResourceProvider.resourceName("img/logo.SVG").endsWith(".SVG"))
  }

  private fun createFile(path: String): VirtualFile = myFixture.tempDirFixture.createFile(path)

  private fun projectRoot(): VirtualFile = myFixture.tempDirFixture.getFile("")!!

  private fun createFileOutsideProject(): VirtualFile {
    val file = FileUtil.createTempFile("markdown-outside", ".png", true)
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)!!
  }

  private fun resolve(source: String, allowOutsideProjectRoot: Boolean = false): Resolution = runBlocking {
    MarkdownImagePathResolver.resolve(document, projectRoot(), source, allowOutsideProjectRoot)
  }

  private fun assertFound(expected: VirtualFile, source: String) {
    val resolution = resolve(source)
    assertInstanceOf(resolution, Resolution.Found::class.java)
    assertEquals(expected, (resolution as Resolution.Found).file)
  }
}
