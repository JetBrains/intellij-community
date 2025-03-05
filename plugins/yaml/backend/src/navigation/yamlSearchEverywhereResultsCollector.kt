// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.navigation

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope

internal fun searchForKey(searchedKey: String, searchScope: GlobalSearchScope, project: Project): Sequence<YamlKeyWithFile> {
  val parsedSearchedKey = YamlKeyParts(searchedKey)

  return findYamlKeysByPattern(parsedSearchedKey.segments, searchScope, project)
    .filter { matchesSearchedKey(it.key, parsedSearchedKey) }
}

private fun matchesSearchedKey(knownKey: String, parsedSearchedKey: YamlKeyParts): Boolean {
  return isSameLengthOrOneSegmentLonger(knownKey, parsedSearchedKey)
         && knownKey.contains(parsedSearchedKey.pattern)
}

private fun isSameLengthOrOneSegmentLonger(knownKey: String, parsedSearchedKey: YamlKeyParts): Boolean {
  val segmentsCountDifference = countKeySeparators(knownKey) - parsedSearchedKey.meaningfulKeySeparatorCount
  return segmentsCountDifference == 0
         || segmentsCountDifference == 1
}

private fun countKeySeparators(key: String): Int {
  return key.count { it == YAML_KEY_SEPARATOR }
}

private const val YAML_KEY_SEPARATOR = '.'

private data class YamlKeyParts(val pattern: String) {
  val segments: List<String> = pattern.split(YAML_KEY_SEPARATOR)
  val meaningfulKeySeparatorCount: Int = countKeySeparators(pattern)
}