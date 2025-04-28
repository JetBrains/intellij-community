package org.jetbrains.plugins.textmate.plist

import java.io.InputStream

@Deprecated("Use JsonOrXmlPlistReader instead")
class CompositePlistReader : PlistReader {
  @Deprecated("Use JsonOrXmlPlistReader instead",
              ReplaceWith("org.jetbrains.plugins.textmate.plist.JsonOrXmlPlistReader(jsonReader = org.jetbrains.plugins.textmate.plist.JsonPlistReader(), xmlReader = org.jetbrains.plugins.textmate.plist.XmlPlistReader())"))
  constructor()
  private val delegate = JsonOrXmlPlistReader(jsonReader = JsonPlistReader(),
                                              xmlReader = XmlPlistReader())

  override fun read(inputStream: InputStream): Plist {
    return inputStream.use {
      delegate.read(inputStream.readBytes())
    }
  }

  override fun read(bytes: ByteArray): Plist {
    return delegate.read(bytes)
  }
}