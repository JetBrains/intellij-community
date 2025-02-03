package org.jetbrains.plugins.textmate.bundles

import java.io.InputStream

interface TextMateResourceReader {
  fun list(relativePath: String): List<String>

  fun read(relativePath: String): InputStream?
}