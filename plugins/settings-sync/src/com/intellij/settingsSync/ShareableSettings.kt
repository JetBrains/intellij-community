package com.intellij.settingsSync

interface ShareableSettings {

  // todo check PersistentStateComponent instead of the fileSpec
  fun isComponentShareable(componentFileSpec: String) : Boolean

}

class AllShareableSettings : ShareableSettings {
  override fun isComponentShareable(componentFileSpec: String): Boolean {
    return true
  }
}