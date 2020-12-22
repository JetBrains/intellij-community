// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.psi.impl

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.AbstractElementManipulator
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.YAMLUtil
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLValue

/**
 * Handles only key manipulation
 */
class YAMLKeyValueKeyManipulator : AbstractElementManipulator<YAMLKeyValue>() {
  override fun handleContentChange(element: YAMLKeyValue, range: TextRange, newContent: String?): YAMLKeyValue? {
    val originalContent = element.getRawKeyText() ?: return element
    if (newContent == null) return element
    val updatedKey = originalContent.replaceRange(range.startOffset, range.endOffset, newContent)

    val generator = YAMLElementGenerator.getInstance(element.project)
    val valueText = when (val value = element.value) {
      is YAMLMapping -> "${preserveIndent(value)}${value.text}"
      else -> element.valueText
    }
    return generator.createYamlKeyValue(updatedKey, valueText).also { element.replace(it) }
  }

  private fun preserveIndent(value: YAMLValue): String {
    val indent = YAMLUtil.getIndentInThisLine(value).takeIf { it > 0 } ?: return ""
    return StringUtil.repeat(" ", indent)
  }

  /**
   * @return range of unquoted key text
   */
  override fun getRangeInElement(element: YAMLKeyValue): TextRange {
    // don't use YAMLKeyValue#keyText since it implicitly unquotes text making it impossible to calculate right range
    val content = element.getRawKeyText() ?: return TextRange.EMPTY_RANGE
    val startOffset = if (content.startsWith("'") || content.startsWith("\"")) 1 else 0
    val endOffset = if (content.length > 1 && (content.endsWith("'") || content.endsWith("\""))) -1 else 0
    return TextRange(startOffset, content.length + endOffset)
  }

  private fun YAMLKeyValue.getRawKeyText(): String? = this.key?.text
}