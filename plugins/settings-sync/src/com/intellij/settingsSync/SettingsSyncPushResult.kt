package com.intellij.settingsSync

import com.intellij.openapi.util.NlsSafe

internal sealed class SettingsSyncPushResult {
  object Success: SettingsSyncPushResult()
  object Rejected: SettingsSyncPushResult()
  class Error(@NlsSafe val message: String): SettingsSyncPushResult()
}

