package com.intellij.mermaid.settings

import com.intellij.openapi.components.BaseState

class MermaidSettingsState : BaseState() {
  var theme by enum(Theme.FOLLOW_IDE)

  enum class Theme(val value: String, val printableName: String) {
    FOLLOW_IDE("follow-ide", "Follow IDE"),
    DEFAULT("default", "Default"),
    NEUTRAL("neutral", "Neutral"),
    DARK("dark", "Dark"),
    FOREST("forest", "Forest"),
    BASE("base", "Base");
  }
}
