package org.jetbrains.plugins.textmate.bundles

import org.jetbrains.plugins.textmate.Constants
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.asSequence

enum class BundleType {
  TEXTMATE,
  SUBLIME,
  VSCODE,
  UNDEFINED;

  companion object {
    /**
     * Detect bundle type by directory.
     *
     * @param directory Bundle directory.
     * @return bundle type.
     * Returns [UNDEFINED] if passed file doesn't exist, or it is not directory
     * or if it doesn't fit to textmate or sublime package.
     */
    fun detectBundleType(directory: Path?): BundleType {
      if (directory != null && directory.isDirectory()) {
        if ("tmBundle".equals(directory.extension, ignoreCase = true)) {
          return TEXTMATE
        }
        val packageJson = directory.resolve(Constants.PACKAGE_JSON_NAME)
        if (packageJson.isRegularFile()) {
          return VSCODE
        }

        val hasTmFiles = runCatching {
          Files.list(directory).use { children ->
            children.asSequence().any { child ->
              child.name.endsWith(".tmLanguage", ignoreCase = true) ||
              child.name.endsWith(".tmPreferences", ignoreCase = true)
            }
          }
        }
        if (hasTmFiles.getOrNull() == true) {
          return SUBLIME
        }

        val infoPlist = directory.resolve(Constants.BUNDLE_INFO_PLIST_NAME)
        val hasInfoPlistFile = infoPlist.isRegularFile()
        if (hasInfoPlistFile) {
          return TEXTMATE
        }
      }
      return UNDEFINED
    }
  }
}
