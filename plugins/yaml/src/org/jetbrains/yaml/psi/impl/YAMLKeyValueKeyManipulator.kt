// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.psi.impl

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.annotations.ApiStatus

/**
 * Handles only key manipulation
 */
@ApiStatus.Experimental
class YAMLKeyValueKeyManipulator : AbstractElementManipulator<YAMLKeyValue>() {
  override fun handleContentChange(element: YAMLKeyValue, range: TextRange, newContent: String?): YAMLKeyValue? {
    if (newContent == null) return null
    val updatedKey = element.keyText.replaceRange(range.startOffset, range.endOffset, newContent)

    val generator = YAMLElementGenerator.getInstance(element.project)
    val value = element.value
    val valueText = when (value) {
      is YAMLMapping -> value.text
      else -> element.valueText
    }
    return generator.createYamlKeyValue(updatedKey, valueText).also { element.replace(it) }
  }
}