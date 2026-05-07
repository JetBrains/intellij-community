package org.jetbrains.plugins.textmate.bundles

import org.jetbrains.plugins.textmate.plist.JsonOrXmlOrYamlPlistReader
import org.jetbrains.plugins.textmate.plist.XmlPlistReader
import org.jetbrains.plugins.textmate.plist.YamlPlistReader
import java.nio.file.Path
import kotlin.io.path.name

@Deprecated("Use readTextMateBundle(fallbackBundleName: String, plistReader: PlistReader, resourceReader: TextMateResourceReader) instead")
fun readTextMateBundle(path: Path): TextMateBundleReader {
  return readTextMateBundle(path.name,
                            JsonOrXmlOrYamlPlistReader(xmlReader = XmlPlistReader(), yamlReader = YamlPlistReader()),
                            TextMateNioResourceReader(path))
}