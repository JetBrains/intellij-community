package com.intellij.mermaid.test

import org.jetbrains.annotations.TestOnly
import java.net.URL

object OfficialDocumentationExamples {
  @TestOnly
  fun obtainBasePath(): URL {
    val url = this::class.java.getResource("examples")
    checkNotNull(url) { "Failed to obtain examples directory url" }
    return url
  }
}
