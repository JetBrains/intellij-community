package com.intellij.ide.starter.ide

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.splitMode

/** IDE process layout used by starter/driver runs. */
enum class IdeRunMode {
  MONOLITH,
  SPLIT;

  fun applyToConfiguration() {
    ConfigurationStorage.splitMode(this == SPLIT)
  }

  companion object {
    fun current(): IdeRunMode {
      return if (ConfigurationStorage.splitMode()) SPLIT else MONOLITH
    }
  }
}
