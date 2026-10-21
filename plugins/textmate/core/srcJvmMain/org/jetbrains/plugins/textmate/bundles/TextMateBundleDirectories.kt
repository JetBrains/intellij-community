package org.jetbrains.plugins.textmate.bundles

import org.jetbrains.plugins.textmate.plist.PlistReaderCore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/**
 * Reads the bundle in [directory]. Returns null when the directory holds no bundle of a known type.
 */
fun readBundleDirectory(directory: Path, plistReader: PlistReaderCore): TextMateBundleReader? {
  val resourceReader = TextMateNioResourceReader(directory)
  return when (BundleType.detectBundleType(resourceReader, directory.name)) {
    BundleType.TEXTMATE -> readTextMateBundle(directory.name, plistReader, resourceReader)
    BundleType.SUBLIME -> readSublimeBundle(directory.name, plistReader, resourceReader)
    BundleType.VSCODE -> readVSCBundle(plistReader, resourceReader)
    BundleType.UNDEFINED -> null
  }
}

/**
 * Builds the index of the bundle directories in [bundlesDirectory], see [TextMateBundlesIndex].
 */
fun buildBundlesIndex(bundlesDirectory: Path, plistReader: PlistReaderCore): TextMateBundlesIndex {
  val entries = listBundleDirectories(bundlesDirectory).mapNotNull { directory ->
    readBundleDirectory(directory, plistReader)?.let { reader ->
      bundleIndexEntry(directory.name, reader.readGrammars().toList())
    }
  }
  return TextMateBundlesIndex(entries)
}

private fun listBundleDirectories(bundlesDirectory: Path): List<Path> {
  return Files.list(bundlesDirectory).use { stream ->
    stream.filter { it.isDirectory() && !it.name.startsWith('.') }.toList()
  }.sortedBy { it.name }
}
