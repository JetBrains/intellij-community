package org.jetbrains.plugins.textmate.bundles

import org.jetbrains.plugins.textmate.Constants
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.streams.asSequence

enum class BundleType {
  TEXTMATE,
  SUBLIME,
  VSCODE,
  UNDEFINED;

  companion object {
    /**
     * Detect a bundle type by directory.
     *
     * @param directory Bundle directory.
     * @return bundle type.
     * Returns [UNDEFINED] if passed file doesn't exist, or it is not directory
     * or if it doesn't fit to textmate or sublime package.
     */
    @JvmStatic
    fun detectBundleType(directory: Path?): BundleType {
      if (directory == null || !Files.isDirectory(directory)) {
        return UNDEFINED
      }

      if (directory.extension.endsWith(".tmBundle", ignoreCase = true)) {
        return TEXTMATE
      }

      val packageJson = directory.resolve(Constants.PACKAGE_JSON_NAME)
      if (Files.isRegularFile(packageJson)) {
        return VSCODE
      }

      val hasTmFiles = runCatching {
        Files.list(directory).use { children ->
          children.asSequence().any { child ->
            val name = child.fileName.toString()
            name.endsWith(".tmLanguage", ignoreCase = true) || name.endsWith(".tmPreferences", ignoreCase = true)
          }
        }
      }
      if (hasTmFiles.getOrNull() == true) {
        return SUBLIME
      }

      val infoPlist = directory.resolve(Constants.BUNDLE_INFO_PLIST_NAME)
      val hasInfoPlistFile = Files.isRegularFile(infoPlist)
      return if (hasInfoPlistFile) TEXTMATE else UNDEFINED
    }
  }
}
