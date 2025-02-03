package org.jetbrains.plugins.textmate.bundles

import org.jetbrains.plugins.textmate.plist.CompositePlistReader
import java.nio.file.Path
import kotlin.io.path.name

@Deprecated("Use readTextMateBundle(fallbackBundleName: String, plistReader: PlistReader, resourceReader: TextMateResourceReader) instead")
fun readTextMateBundle(path: Path): TextMateBundleReader {
  return readTextMateBundle(path.name, CompositePlistReader(), TextMateNioResourceReader(path))
}