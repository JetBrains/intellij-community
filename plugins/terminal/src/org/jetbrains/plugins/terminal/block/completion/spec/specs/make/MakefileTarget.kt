// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.specs.make

import com.intellij.util.text.nullize

/**
 * Represents makefile target (e.g. task, command) header.
 *
 * Example:
 * `target-name: dependency1 dependency2 # Comment`
 */
internal class MakefileTarget(
  val name: String,
  val dependencies: List<String>,
  val comment: String?
) {

  companion object {
    private const val MAKEFILE_TARGET_DECLARATION_REGEX_STR = "^([a-zA-Z_-]+):([a-zA-Z_\\- ]*)?(#+ .*)?\$"
    private val MAKEFILE_TARGET_DECLARATION_REGEX = MAKEFILE_TARGET_DECLARATION_REGEX_STR.toRegex()

    /**
     * Parses the header of makefile target.
     * @param makefileTarget Example: `target-name: dependency1 dependency2 # Comment`
     */
    fun parse(makefileTarget: String): MakefileTarget? {
      if (!makefileTarget.matches(MAKEFILE_TARGET_DECLARATION_REGEX)) {
        return null
      }

      val semicolonIdx = makefileTarget.indexOf(":")

      if (semicolonIdx < 0) {
        return null
      }

      val targetName = makefileTarget
        .substringBefore(':', "")
        .trim()
        .nullize() ?: return null

      val dependencies = makefileTarget
        .substringAfter(':', "")
        .substringBefore('#')
        .trim()
        .takeIf(String::isNotBlank)
        ?.split("\\s+".toRegex())
                         ?: emptyList()

      val comment = makefileTarget
        .substringAfter('#', "")
        .dropWhile { char -> char == '#' }
        .trim()
        .takeIf(String::isNotBlank)


      return MakefileTarget(targetName, dependencies, comment)
    }
  }

}
