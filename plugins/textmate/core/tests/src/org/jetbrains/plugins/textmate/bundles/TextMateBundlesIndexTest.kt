package org.jetbrains.plugins.textmate.bundles

import com.intellij.openapi.application.PathManager
import org.jetbrains.plugins.textmate.plist.JsonOrXmlOrYamlPlistReader
import org.jetbrains.plugins.textmate.plist.XmlPlistReaderForTests
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks that `lib/bundles/index.txt` describes the bundles in `lib/bundles`.
 * `loadVSCBundles.sh` writes the index. A run with `-Dtextmate.bundles.index.update=true` writes it from the bundles too.
 */
class TextMateBundlesIndexTest {
  @Test
  fun indexMatchesBundles() {
    val bundlesDir = Path.of(PathManager.getCommunityHomePath()).resolve("plugins/textmate/lib/bundles")
    val indexFile = bundlesDir.resolve(BUNDLES_INDEX_FILE_NAME)
    val expected = buildBundlesIndex(bundlesDir, JsonOrXmlOrYamlPlistReader(xmlReader = XmlPlistReaderForTests()))
    if (System.getProperty("textmate.bundles.index.update").toBoolean()) {
      indexFile.writeText(expected.encode())
    }
    val actual = TextMateBundlesIndex.decode(indexFile.readText())
    assertEquals(expected, actual,
                 "$indexFile does not match the bundles, run loadVSCBundles.sh or this test with -Dtextmate.bundles.index.update=true")
  }

  @Test
  fun encodeAndDecodeKeepTheIndex() {
    val index = TextMateBundlesIndex(listOf(
      TextMateBundleIndexEntry("empty", emptyList()),
      TextMateBundleIndexEntry("bazel", listOf(
        TextMateGrammarIndexEntry("source.bazelrc", extensions = listOf("bazelrc"), fileNames = listOf(".bazelrc", "bazel.rc")),
        TextMateGrammarIndexEntry("source.starlark", extensions = listOf("bzl", "BUILD", "sky"), patterns = listOf("*.BUILD.bazel")),
      )),
    ))
    val encoded = index.encode()
    assertTrue(encoded.contains("\nscope source.bazelrc\next bazelrc\nname .bazelrc;bazel.rc\nscope source.starlark\next bzl;BUILD;sky\npattern *.BUILD.bazel\n"), encoded)
    assertEquals(index, TextMateBundlesIndex.decode("# a comment\n\n$encoded"))
  }
}
