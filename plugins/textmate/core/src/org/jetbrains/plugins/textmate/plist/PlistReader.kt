package org.jetbrains.plugins.textmate.plist

import java.io.*
import kotlin.jvm.Throws

interface PlistReader {
  @Throws(IOException::class)
  fun read(inputStream: InputStream): Plist
}
