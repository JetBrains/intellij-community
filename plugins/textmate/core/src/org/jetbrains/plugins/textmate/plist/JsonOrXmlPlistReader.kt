package org.jetbrains.plugins.textmate.plist

class JsonOrXmlPlistReader(
  private val jsonReader: PlistReaderCore,
  private val xmlReader: PlistReaderCore,
) : PlistReaderCore {
  override fun read(bytes: ByteArray): Plist {
    val symbol = bytes.asSequence().take(256).map { it.toInt().toChar() }.firstOrNull { !it.isWhitespace() }
    return when (symbol) {
      '{' -> {
        jsonReader.read(bytes)
      }
      '<' -> {
        xmlReader.read(bytes)
      }
      else -> {
        throw IllegalArgumentException("Unknown bundle type, first char: $symbol")
      }
    }
  }
}