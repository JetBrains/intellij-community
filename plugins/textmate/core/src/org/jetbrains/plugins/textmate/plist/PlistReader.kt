package org.jetbrains.plugins.textmate.plist

import java.io.*

interface PlistReader {
  @Throws(IOException::class)
  fun read(inputStream: InputStream): Plist
}
