// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.preview

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.TrustedProjectsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.plus
import org.intellij.plugins.markdown.ui.preview.MarkdownImageResourceProvider
import org.intellij.plugins.markdown.ui.preview.MarkdownImageWatcher
import org.intellij.plugins.markdown.util.MarkdownPluginScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownImageWatcherTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @TestDisposable
  lateinit var disposable: Disposable

  private lateinit var document: VirtualFile
  private lateinit var nearImage: VirtualFile
  private lateinit var farImage: VirtualFile
  private lateinit var provider: MarkdownImageResourceProvider

  @BeforeEach
  fun setUp() {
    nearImage = createFile("subdir/img/near.png")
    farImage = createFile("img/far.png")
    document = createFile("subdir/subdir.md")
  }

  @Test
  fun `test a change of a shown image gives it a new version`() {
    val watcher = watching("img/near.png", "../img/far.png")
    writeContent(farImage)
    assertNull(watcher.versionOf("img/near.png"))
    writeContent(nearImage)
    assertEquals(nearImage.modificationStamp, watcher.versionOf("img/near.png"))
  }

  @Test
  fun `test each change of a shown image gives a greater version`() {
    val watcher = watching("img/near.png")
    writeContent(nearImage)
    val first = watcher.versionOf("img/near.png")!!
    writeContent(nearImage)
    assertTrue(watcher.versionOf("img/near.png")!! > first)
  }

  @Test
  fun `test an image that the document no longer shows gets no version`() {
    val watcher = watching("img/near.png")
    watcher.watch(emptySet())
    writeContent(nearImage)
    assertNull(watcher.versionOf("img/near.png"))
  }

  @Test
  fun `test a missing image gets a version when its file appears`() {
    val watcher = watching("img/later.png")
    runWriteActionAndWait { nearImage.parent.createChildData(this, "other.png") }
    assertNull(watcher.versionOf("img/later.png"))
    runWriteActionAndWait { nearImage.parent.createChildData(this, "later.png") }
    assertNotNull(watcher.versionOf("img/later.png"))
  }

  @Test
  fun `test a nearer file that appears again gets a version`() {
    val near = createFile("subdir/ambiguous.png", "near")
    createFile("ambiguous.png", "far")
    val watcher = watching("ambiguous.png")
    assertEquals("near", loadAmbiguous())
    runWriteActionAndWait { near.delete(this) }
    val afterDelete = watcher.versionOf("ambiguous.png")!!
    assertEquals("far", loadAmbiguous())
    runWriteActionAndWait { document.parent.createChildData(this, "ambiguous.png").setBinaryContent("near".toByteArray()) }
    assertTrue(watcher.versionOf("ambiguous.png")!! > afterDelete)
    assertEquals("near", loadAmbiguous())
  }

  @Test
  fun `test a file with the same name in another directory gives no version`() {
    val watcher = watching("img/near.png")
    runWriteActionAndWait { farImage.parent.createChildData(this, "near.png") }
    assertNull(watcher.versionOf("img/near.png"))
  }

  @Test
  fun `test a renamed directory on the path of an image gives it a version`() {
    val watcher = watching("img/near.png")
    runWriteActionAndWait { nearImage.parent.rename(this, "moved") }
    assertNotNull(watcher.versionOf("img/near.png"))
  }

  @Test
  fun `test a directory with another name gives no version`() {
    val watcher = watching("img/near.png")
    runWriteActionAndWait { document.parent.createChildDirectory(this, "other") }
    assertNull(watcher.versionOf("img/near.png"))
  }

  @Test
  fun `test an image deleted and restored in one action gets a version`() {
    val watcher = watching("img/near.png")
    val directory = nearImage.parent
    runWriteActionAndWait {
      nearImage.delete(this)
      directory.createChildData(this, "near.png").setBinaryContent("restored".toByteArray())
    }
    assertNotNull(watcher.versionOf("img/near.png"))
  }

  @Test
  fun `test a deleted image gets the version of a missing file`() {
    val watcher = watching("img/near.png")
    runWriteActionAndWait { nearImage.delete(this) }
    assertEquals(MarkdownImageWatcher.MISSING, watcher.versionOf("img/near.png"))
  }

  @Test
  fun `test the watcher resolves with the rule of its caller`() {
    val scope = MarkdownPluginScope.createChildScope(myFixture.project) + Dispatchers.Unconfined
    Disposer.register(disposable) { scope.cancel() }
    val watcher = MarkdownImageWatcher<Unit>(scope) { null }
    watcher.watch(setOf("img/later.png"))
    watcher.onImageLoaded("img/later.png", null)
    runWriteActionAndWait { nearImage.parent.createChildData(this, "later.png") }
    assertNull(watcher.versionOf("img/later.png"))
  }

  @Test
  fun `test an untrusted project cannot load an image`() {
    TrustedProjectsTestUtil.withTrustedProjectsCheckEnabled {
      TrustedProjects.setProjectTrusted(myFixture.project, false)
      try {
        provider = MarkdownImageResourceProvider(myFixture.project, document)
        assertNull(provider.loadResource(MarkdownImageResourceProvider.resourceName("img/near.png")))
      }
      finally {
        TrustedProjects.setProjectTrusted(myFixture.project, true)
      }
    }
  }

  @Test
  fun `test the watcher reports the changed images`() {
    val scope = MarkdownPluginScope.createChildScope(myFixture.project) + Dispatchers.Unconfined
    Disposer.register(disposable) { scope.cancel() }
    val reported = ArrayList<Set<String>>()
    val watcher = MarkdownImageWatcher<String>(scope, onChanged = { reported += it }) { provider.resolveFile(it) }
    provider = MarkdownImageResourceProvider(myFixture.project, document, watcher)
    watcher.watch(setOf("img/near.png", "../img/far.png"))
    provider.loadResource(MarkdownImageResourceProvider.resourceName("img/near.png"))
    provider.loadResource(MarkdownImageResourceProvider.resourceName("../img/far.png"))
    writeContent(nearImage)
    assertEquals(listOf(setOf("img/near.png")), reported)
  }

  @Test
  fun `test a load that ends before the watch keeps its record`() {
    val watcher = bareWatcher()
    watcher.onImageLoaded("img/near.png", nearImage, "size")
    watcher.watch(setOf("img/near.png"))
    writeContent(nearImage)
    assertEquals(nearImage.modificationStamp, watcher.versionOf("img/near.png"))
  }

  @Test
  fun `test a change of the file drops the loaded value`() {
    val watcher = bareWatcher()
    watcher.watch(setOf("img/near.png"))
    watcher.onImageLoaded("img/near.png", nearImage, "size")
    assertEquals("size", watcher.loadedValueOf("img/near.png"))
    writeContent(nearImage)
    assertNull(watcher.loadedValueOf("img/near.png"))
  }

  @Test
  fun `test an image that leaves the document drops its record`() {
    val watcher = bareWatcher()
    watcher.onImageLoaded("img/near.png", nearImage, "size")
    watcher.watch(setOf("img/near.png"))
    watcher.watch(emptySet())
    assertNull(watcher.loadedValueOf("img/near.png"))
  }

  private fun bareWatcher(): MarkdownImageWatcher<String> {
    val scope = MarkdownPluginScope.createChildScope(myFixture.project) + Dispatchers.Unconfined
    Disposer.register(disposable) { scope.cancel() }
    return MarkdownImageWatcher<String>(scope) { provider.resolveFile(it) }.also {
      provider = MarkdownImageResourceProvider(myFixture.project, document, it)
    }
  }

  private fun watching(vararg sources: String): MarkdownImageWatcher<String> {
    // An unconfined scope checks each file event before the write action ends.
    val scope = MarkdownPluginScope.createChildScope(myFixture.project) + Dispatchers.Unconfined
    Disposer.register(disposable) { scope.cancel() }
    val watcher = MarkdownImageWatcher<String>(scope) { provider.resolveFile(it) }
    provider = MarkdownImageResourceProvider(myFixture.project, document, watcher)
    watcher.watch(sources.toSet())
    for (source in sources) {
      provider.loadResource(MarkdownImageResourceProvider.resourceName(source))
    }
    return watcher
  }

  private fun loadAmbiguous(): String? {
    return provider.loadResource(MarkdownImageResourceProvider.resourceName("ambiguous.png"))?.content?.decodeToString()
  }

  private fun writeContent(file: VirtualFile) {
    runWriteActionAndWait { file.setBinaryContent(byteArrayOf(1, 2, 3)) }
  }

  private fun createFile(path: String, content: String = ""): VirtualFile = myFixture.tempDirFixture.createFile(path, content)
}
