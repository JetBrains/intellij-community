package org.jetbrains.plugins.textmate.plist

class JsonOrXmlOrYamlPlistReader(
  private val jsonReader: PlistReaderCore = JsonPlistReader(),
  private val xmlReader: PlistReaderCore,
  private val yamlReader: PlistReaderCore? = null,
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
        val reader = yamlReader ?: throw IllegalArgumentException("Unknown bundle type, first char: $symbol")
        runCatching {
          reader.read(bytes)
        }.getOrElse { cause ->
          throw IllegalArgumentException("Unknown bundle type, first char: $symbol", cause)
        }
      }
    }
  }
}