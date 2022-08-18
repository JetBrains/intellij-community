package com.intellij.settingsSync

import com.intellij.DynamicBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val PATH_TO_BUNDLE = "messages.SettingsSyncBundle"

@ApiStatus.Internal
object SettingsSyncBundle : DynamicBundle(PATH_TO_BUNDLE) {

  fun message(@PropertyKey(resourceBundle = PATH_TO_BUNDLE) key: String, vararg params: Any) : @Nls String {
    return getMessage(key, *params)
  }
}