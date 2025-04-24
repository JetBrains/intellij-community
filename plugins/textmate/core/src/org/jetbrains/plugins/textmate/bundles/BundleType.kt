package org.jetbrains.plugins.textmate.bundles

import org.jetbrains.plugins.textmate.Constants

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
    fun detectBundleType(resourceReader: TextMateResourceReader, bundleName: String): BundleType {
      if (bundleName.endsWith(".tmBundle", ignoreCase = true)) {
        return TEXTMATE
      }
      val childrenFiles = resourceReader.list(".").toSet()
      val hasPackageJson = childrenFiles.contains(Constants.PACKAGE_JSON_NAME)
      if (hasPackageJson) {
        return VSCODE
      }

      val hasTmFiles = childrenFiles.any { name ->
        name.endsWith(".tmLanguage", ignoreCase = true) ||
        name.endsWith(".tmPreferences", ignoreCase = true)
      }
      if (hasTmFiles) {
        return SUBLIME
      }

      val hasInfoPlistFile = childrenFiles.contains(Constants.BUNDLE_INFO_PLIST_NAME)
      if (hasInfoPlistFile) {
        return TEXTMATE
      }
      return UNDEFINED
    }
  }
}
