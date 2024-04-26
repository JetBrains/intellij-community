package com.intellij.settingsSync.migration

import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

class ConfigInfo(val id: @NonNls String, val configClass: Class<*>, val description: @Nls String) 