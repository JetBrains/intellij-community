package org.jetbrains.plugins.textmate.plist

interface PlistReaderCore {
  fun read(bytes: ByteArray): Plist
}
