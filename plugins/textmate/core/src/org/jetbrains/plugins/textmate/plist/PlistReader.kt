package org.jetbrains.plugins.textmate.plist

import java.io.*

interface PlistReader {
  @Deprecated("use {@link #read(InputStream)}")
  @Throws(IOException::class)
  fun read(file: File): Plist {
    file.inputStream().buffered().use {
      return read(it)
    }
  }

  @Throws(IOException::class)
  fun read(inputStream: InputStream): Plist
}
