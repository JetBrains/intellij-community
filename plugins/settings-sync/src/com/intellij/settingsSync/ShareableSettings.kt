package com.intellij.settingsSync

internal interface ShareableSettings {

  // todo check PersistentStateComponent instead of the fileSpec
  fun isComponentShareable(componentFileSpec: String) : Boolean

}

internal class AllShareableSettings : ShareableSettings {
  override fun isComponentShareable(componentFileSpec: String): Boolean {
    return true
  }
}