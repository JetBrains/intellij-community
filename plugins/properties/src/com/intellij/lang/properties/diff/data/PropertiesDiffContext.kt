// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.diff.data

import com.intellij.diff.fragments.LineFragment
import com.intellij.lang.properties.diff.FileInfoHolder

/**
 * Stores the necessary information to construct new ranges.
 *
 * @property disabledRangeMap - all [LineFragment] and ranges which intersect with them and should be ignored
 * @property modifiedPropertyList - stores all properties which were modified in the file. Highlighting will be re-enabled for them
 *
 * @see com.intellij.lang.properties.diff.PropertiesDiffLangSpecificProvider.createPatchedDiffRangeList
 */
internal data class PropertiesDiffContext(
  val fileInfoHolder: FileInfoHolder,
  val disabledRangeMap: Map<LineFragment, List<UnchangedRangeInfo>>,
  val modifiedPropertyList: List<ModifiedPropertyRange>,
)
