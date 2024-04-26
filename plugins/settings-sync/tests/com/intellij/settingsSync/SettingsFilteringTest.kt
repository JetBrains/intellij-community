package com.intellij.settingsSync

import com.intellij.configurationStore.getPerOsSettingsStorageFolderName
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.settingsSync.config.EDITOR_FONT_SUBCATEGORY_ID
import com.intellij.testFramework.LightPlatformTestCase

class SettingsFilteringTest : LightPlatformTestCase() {

  fun `test editor settings sync enabled via Code category` () {
    assertTrue(isSyncCategoryEnabled("editor.xml"))
    SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.CODE, false)
    try {
      assertFalse(isSyncCategoryEnabled("editor.xml"))
    }
    finally {
      SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.CODE, true)
    }
  }

  fun `test color scheme sync enabled via UI category` () {
    if (!isSyncCategoryEnabled("colors/my_scheme.icls")) {
      EditorColorsManagerImpl() // ensure that color scheme manager is initialized and registered
    }
    assertTrue(isSyncCategoryEnabled("colors/my_scheme.icls"))
    SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.UI, false)
    try {
      assertFalse(isSyncCategoryEnabled("colors/my_scheme.icls"))
    }
    finally {
      SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.UI, true)
    }
  }

  fun `test passing the whole scheme storage directory`() {
    assertTrue(isSyncCategoryEnabled("keymaps"))
    SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.KEYMAP, false)
    try {
      assertFalse(isSyncCategoryEnabled("keymaps"))
    }
    finally {
      SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.KEYMAP, true)
    }
  }

  fun `test font sync enabled via subcategory` () {
    assertTrue(isSyncCategoryEnabled("editor-font.xml"))
    SettingsSyncSettings.getInstance().setSubcategoryEnabled(SettingsCategory.UI, EDITOR_FONT_SUBCATEGORY_ID, false)
    try {
      assertFalse(isSyncCategoryEnabled("editor-font.xml"))
    }
    finally {
      SettingsSyncSettings.getInstance().setSubcategoryEnabled(SettingsCategory.UI, EDITOR_FONT_SUBCATEGORY_ID, true)
    }
  }

  fun `test keymap settings enabled via Keymap category` () {
    val osPrefix = getPerOsSettingsStorageFolderName() + "/"
    val fileSpec = osPrefix + "keymap.xml"
    assertTrue(isSyncCategoryEnabled(fileSpec))
    SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.KEYMAP, false)
    try {
      assertFalse(isSyncCategoryEnabled(fileSpec))
    }
    finally {
      SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.KEYMAP, true)
    }
  }

  fun `test settings sync settings always synchronized` () {
    assertTrue(isSyncCategoryEnabled("settingsSync.xml"))
  }
}