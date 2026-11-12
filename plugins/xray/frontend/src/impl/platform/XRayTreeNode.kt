package com.intellij.xray.frontend.impl.platform

import com.intellij.codeInsight.hints.InlayHintSettingsKey
import com.intellij.ui.CheckedTreeNode
import com.intellij.xray.frontend.api.InlayHintStage

sealed class StageKey {
  data class LeafSettingsKey(val key: InlayHintSettingsKey) : StageKey()
  data class GroupSettingsKey(val key: String) : StageKey()
}

internal open class XRayTreeNode(userObject: Any?, val stageKey: StageKey?): CheckedTreeNode(userObject) {

  constructor() : this(null, null)

  var stage: InlayHintStage = InlayHintStage.DISABLED
    set(value) { field = value; isChecked = value.isEnabled }

  override fun isChecked(): Boolean {
    if (stageKey == null) return super.isChecked()
    return stage.isEnabled
  }

  override fun setChecked(checked: Boolean) {
    if (stageKey == null) return super.setChecked(checked)

    stage = stage.next()
  }
}
