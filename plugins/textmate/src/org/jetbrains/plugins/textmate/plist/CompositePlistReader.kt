package org.jetbrains.plugins.textmate.plist

import java.io.InputStream

@Deprecated("Use JsonOrXmlOrYamlPlistReader instead")
class CompositePlistReader : PlistReader {
  @Deprecated("Use JsonOrXmlOrYamlPlistReader instead",
              ReplaceWith("org.jetbrains.plugins.textmate.plist.JsonOrXmlOrYamlPlistReader(jsonReader = org.jetbrains.plugins.textmate.plist.JsonPlistReader(), xmlReader = org.jetbrains.plugins.textmate.plist.XmlPlistReader(), yamlReader = org.jetbrains.plugins.textmate.plist.YamlPlistReader())"))
  constructor()
  private val delegate = JsonOrXmlOrYamlPlistReader(xmlReader = XmlPlistReader(), yamlReader = YamlPlistReader())

  override fun read(inputStream: InputStream): Plist {
    return inputStream.use {
      delegate.read(inputStream.readBytes())
    }
  }

  override fun read(bytes: ByteArray): Plist {
    return delegate.read(bytes)
  }
}