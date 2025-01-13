package org.jetbrains.plugins.textmate.plist

import java.io.IOException
import java.io.InputStream

class CompositePlistReader : PlistReader {
  private val myJsonReader: PlistReader = JsonPlistReader()
  private val myXmlReader: PlistReader = XmlPlistReader()

  @Throws(IOException::class)
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
      return myJsonReader.read(inputStream)
    }
    if (symbol == '<'.code) {
      return myXmlReader.read(inputStream)
    }
    throw IOException("Unknown bundle type, first char: " + symbol.toChar())
  }
}
