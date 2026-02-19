// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import java.net.URI
import java.net.URISyntaxException

class Maven4SchemaVersionChecker {
  companion object {
    fun is410Xsd(url: String): Boolean {
      try {
        val fileName = URI(url)?.path?.substringAfterLast("/") ?: return false
        return maven410Patterns.any { fileName.contains(it) }
               && maven400Patterns.none { fileName.contains(it) }
      }
      catch (_: URISyntaxException) {
        return false
      }
    }

    private val maven410Patterns = setOf("4.1.0", "4_1_0")
    private val maven400Patterns = setOf("4.0.0")

  }
}