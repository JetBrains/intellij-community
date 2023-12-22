package com.intellij.settingsSync.git

import com.intellij.openapi.Disposable
import com.intellij.vcs.log.impl.VcsLogUiProperties

// Properties are not needed for settings history toolwindow, so it's just a stub
class SettingsHistoryUiProperties : VcsLogUiProperties {
  override fun <T> get(property: VcsLogUiProperties.VcsLogUiProperty<T>): T & Any {
    throw UnsupportedOperationException("Unsupported $property")
  }

  override fun <T> set(property: VcsLogUiProperties.VcsLogUiProperty<T>, value: T & Any) = Unit
  override fun <T : Any?> exists(property: VcsLogUiProperties.VcsLogUiProperty<T>): Boolean = false
  override fun addChangeListener(listener: VcsLogUiProperties.PropertiesChangeListener) = Unit
  override fun addChangeListener(listener: VcsLogUiProperties.PropertiesChangeListener, parent: Disposable) = Unit
  override fun removeChangeListener(listener: VcsLogUiProperties.PropertiesChangeListener) = Unit
}