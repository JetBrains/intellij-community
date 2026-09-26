package org.jetbrains.plugins.textmate.plist

import java.io.IOException
import java.io.InputStream

@Deprecated("Use PlistReaderCore instead")
interface PlistReader: PlistReaderCore {
  @Throws(IOException::class)
  fun read(inputStream: InputStream): Plist
}
