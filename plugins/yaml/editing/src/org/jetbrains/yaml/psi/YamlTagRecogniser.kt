package org.jetbrains.yaml.psi

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsSafe

interface YamlTagRecogniser {
  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName.create<YamlTagRecogniser>("com.intellij.yaml.tagRecogniser")
  }

  fun isRecognizedTag(tagText: @NlsSafe String): Boolean
}