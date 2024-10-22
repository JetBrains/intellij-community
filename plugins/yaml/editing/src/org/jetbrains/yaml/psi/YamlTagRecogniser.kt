package org.jetbrains.yaml.psi

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus

/**
 * Interface for recognising known domain-specific YAML tags.
 *
 * This interface allows implementations to inform the IDE about YAML tags that are expected in a particular YAML-based framework.
 * If a tag is recognised by any implementation, no warnings will be reported in the editor.
 */
@ApiStatus.Experimental
interface YamlTagRecogniser {
  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName.create<YamlTagRecogniser>("com.intellij.yaml.tagRecogniser")
  }

  /**
   * Checks if the provided tag text is recognised as a known domain-specific YAML tag.
   */
  fun isRecognizedTag(tagText: @NlsSafe String): Boolean
}