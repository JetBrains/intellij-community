package com.intellij.settingsSync

import com.intellij.configurationStore.getPerOsSettingsStorageFolderName
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SettingsCategory
import com.intellij.settingsSync.config.SettingsSyncUiGroup
import com.intellij.testFramework.LightPlatformTestCase

class SettingsFilteringTest : LightPlatformTestCase() {

  fun `test editor settings sync enabled via Code category` () {
    assertTrue(isSyncEnabled("editor.xml", RoamingType.DEFAULT))
    SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.CODE, false)
    try {
      assertFalse(isSyncEnabled("editor.xml", RoamingType.DEFAULT))
    }
    finally {
      SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.CODE, true)
    }
  }

  fun `test color scheme sync enabled via UI category` () {
    assertTrue(isSyncEnabled("colors/my_scheme.icls", RoamingType.DEFAULT))
    SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.UI, false)
    try {
      assertFalse(isSyncEnabled("colors/my_scheme.icls", RoamingType.DEFAULT))
    }
    finally {
      SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.UI, true)
    }
  }

  fun `test font sync enabled via subcategory` () {
    assertTrue(isSyncEnabled("editor-font.xml", RoamingType.DEFAULT))
    SettingsSyncSettings.getInstance().setSubcategoryEnabled(SettingsCategory.UI, SettingsSyncUiGroup.EDITOR_FONT_ID, false)
    try {
      assertFalse(isSyncEnabled("editor-font.xml", RoamingType.DEFAULT))
    }
    finally {
      SettingsSyncSettings.getInstance().setSubcategoryEnabled(SettingsCategory.UI, SettingsSyncUiGroup.EDITOR_FONT_ID, true)
    }
  }

  fun `test keymap settings enabled via Keymap category` () {
    val osPrefix = getPerOsSettingsStorageFolderName() + "/"
    val fileSpec = osPrefix + "keymap.xml"
    assertTrue(isSyncEnabled(fileSpec, RoamingType.PER_OS))
    SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.KEYMAP, false)
    try {
      assertFalse(isSyncEnabled(fileSpec, RoamingType.PER_OS))
    }
    finally {
      SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.KEYMAP, true)
    }
  }

  fun `test settings sync settings always synchronized` () {
    assertTrue(isSyncEnabled("settingsSync.xml", RoamingType.DEFAULT))
  }
}