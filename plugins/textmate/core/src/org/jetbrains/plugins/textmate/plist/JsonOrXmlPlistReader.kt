package org.jetbrains.plugins.textmate.plist

import java.io.IOException
import java.io.InputStream
import kotlin.jvm.Throws

class JsonOrXmlPlistReader(
  private val jsonReader: PlistReader,
  private val xmlReader: PlistReader,
) : PlistReader {
  @Throws
  override fun read(inputStream: InputStream): Plist {
    inputStream.mark(256)
    var symbol = inputStream.read()
    var tries = 0
    while (symbol > 0 && symbol.toChar().isWhitespace() && tries < 255) {
      symbol = inputStream.read()
      tries++
    }
    inputStream.reset()
    if (symbol == '{'.code) {
      return jsonReader.read(inputStream)
    }
    if (symbol == '<'.code) {
      return xmlReader.read(inputStream)
    }
    throw IOException("Unknown bundle type, first char: " + symbol.toChar())
  }
}