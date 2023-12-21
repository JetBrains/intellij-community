package com.intellij.settingsSync.git

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.vcs.log.impl.VcsLogApplicationSettings
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogUiPropertiesImpl

// Properties are not needed for settings history toolwindow, so it's just a stub
class SettingsHistoryUiProperties<T : VcsLogUiPropertiesImpl.State> : VcsLogUiPropertiesImpl<VcsLogUiPropertiesImpl.State>(
  ApplicationManager.getApplication().getService(VcsLogApplicationSettings::class.java)) {
  override val logUiState: State = State()

  override fun <T : Any?> exists(property: VcsLogUiProperties.VcsLogUiProperty<T>): Boolean {
    return false
  }

  override fun addChangeListener(listener: VcsLogUiProperties.PropertiesChangeListener) {
  }

  override fun addChangeListener(listener: VcsLogUiProperties.PropertiesChangeListener, parent: Disposable) {
  }

  override fun removeChangeListener(listener: VcsLogUiProperties.PropertiesChangeListener) {
  }

  override fun addRecentlyFilteredGroup(filterName: String, values: MutableCollection<String>) {
  }

  override fun getRecentlyFilteredGroups(filterName: String): List<List<String>> {
    return listOf()
  }

  override fun getFilterValues(filterName: String): MutableList<String>? {
    return null
  }

  override fun saveFilterValues(filterName: String, values: List<String>?) {
  }
}