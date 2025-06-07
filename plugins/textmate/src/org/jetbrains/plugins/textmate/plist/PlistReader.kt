package org.jetbrains.plugins.textmate.plist

import java.io.*
import kotlin.jvm.Throws

@Deprecated("Use PlistReaderCore instead")
interface PlistReader: PlistReaderCore {
  @Throws(IOException::class)
  fun read(inputStream: InputStream): Plist
}
