package org.jetbrains.plugins.textmate

import com.intellij.openapi.application.PathManager
import org.jetbrains.plugins.textmate.bundles.TextMateNioResourceReader
import org.jetbrains.plugins.textmate.bundles.TextMateResourceReader
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString

/**
 * This file contains declarations that delegate to NOT multiplatform-ready code.
 *
 * When compiling a multiplatform project, all functions should be replaced with `expect` ones
 * and every target should provide its own implementation.
 */

object TestUtilMultiplatform {
  fun getBundleDirectoryPath(bundleName: String): String {
    val bundleDirectory = Path.of(PathManager.getCommunityHomePath()).resolve("plugins/textmate/testData/bundles").resolve(bundleName)
    return if (bundleDirectory.exists()) {
      bundleDirectory
    }
    else {
      Path.of(PathManager.getCommunityHomePath()).resolve("plugins/textmate/lib/bundles").resolve(bundleName)
    }.pathString
  }

  fun getResourceReader(bundleName: String): TextMateResourceReader {
    val bundleDirectory = getBundleDirectoryPath(bundleName)
    return TextMateNioResourceReader(Path(bundleDirectory))
  }
}