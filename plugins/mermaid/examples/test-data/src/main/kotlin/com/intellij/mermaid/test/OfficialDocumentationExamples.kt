package com.intellij.mermaid.test

import org.jetbrains.annotations.TestOnly
import java.net.URL
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.toPath

object OfficialDocumentationExamples {
  @TestOnly
  fun obtainBasePath(): URL {
    val url = this::class.java.getResource("examples")
    checkNotNull(url) { "Failed to obtain examples directory url" }
    return url
  }

  @TestOnly
  fun obtainExamples(block: (List<Path>) -> Unit) {
    val uri = obtainBasePath().toURI()
    val fileSystem = FileSystems.newFileSystem(uri, mutableMapOf<String, Any>())
    fileSystem.use {
      block(uri.toPath().listDirectoryEntries())
    }
  }
}
