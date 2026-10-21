package com.intellij.ide.starter.driver.reused.recovery

import com.intellij.driver.client.Driver
import com.intellij.driver.model.RdTarget
import com.intellij.ide.starter.ide.IDETestContext
import kotlin.io.path.exists

internal fun SettingsRecoveryPlan.changedFileSpecsWithRecentProjects(restoredRecentProjectsSettingsExists: Boolean): Set<String> {
  return if (restoredRecentProjectsSettingsExists) {
    changedFileSpecs + RECENT_PROJECTS_SETTINGS_FILE
  }
  else {
    changedFileSpecs - RECENT_PROJECTS_SETTINGS_FILE
  }
}

internal fun SettingsRecoveryPlan.deletedFileSpecsWithRecentProjects(restoredRecentProjectsSettingsExists: Boolean): Set<String> {
  return if (restoredRecentProjectsSettingsExists) {
    deletedFileSpecs - RECENT_PROJECTS_SETTINGS_FILE
  }
  else {
    deletedFileSpecs + RECENT_PROJECTS_SETTINGS_FILE
  }
}

internal fun Driver.settingsReloadTargets(): Set<RdTarget> {
  return if (isRemDevMode ) linkedSetOf(RdTarget.DEFAULT, RdTarget.BACKEND) else linkedSetOf(RdTarget.DEFAULT)
}

internal fun IDETestContext.recentProjectsSettingsFileExists(): Boolean {
  return paths.configDir.resolve("options").resolve(RECENT_PROJECTS_SETTINGS_FILE).exists()
}

private const val RECENT_PROJECTS_SETTINGS_FILE = "recentProjects.xml"
