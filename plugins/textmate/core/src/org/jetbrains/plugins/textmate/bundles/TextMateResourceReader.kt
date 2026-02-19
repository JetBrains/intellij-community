package org.jetbrains.plugins.textmate.bundles

interface TextMateResourceReader {
  fun list(relativePath: String): List<String>

  fun read(relativePath: String): ByteArray?
}