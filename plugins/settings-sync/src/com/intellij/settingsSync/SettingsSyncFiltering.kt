package com.intellij.settingsSync

import com.intellij.configurationStore.getPerOsSettingsStorageFolderName
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.keymap.impl.KEYMAPS_DIR_PATH
import com.intellij.openapi.util.text.StringUtil
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemesImpl
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.settingsSync.config.SettingsSyncUiGroup
import com.intellij.settingsSync.plugins.SettingsSyncPluginManager

internal fun isSyncEnabled(fileSpec: String, roamingType: RoamingType): Boolean {
  if (roamingType == RoamingType.DISABLED) return false
  val rawFileSpec = removeOsPrefix(fileSpec)
  if (rawFileSpec == SettingsSyncSettings.FILE_SPEC ||
      rawFileSpec == SettingsSyncPluginManager.FILE_SPEC) return true
  val componentClasses = findComponentClasses(rawFileSpec)
  val category = getSchemeCategory(rawFileSpec) ?: getCategory(componentClasses)
  if (category != SettingsCategory.OTHER && SettingsSyncSettings.getInstance().isCategoryEnabled(category)) {
    val subCategory = getSubCategory(componentClasses)
    if (subCategory != null) {
      return SettingsSyncSettings.getInstance().isSubcategoryEnabled(category, subCategory)
    }
    return true
  }
  return false
}

private fun removeOsPrefix(fileSpec: String): String {
  val osPrefix = getPerOsSettingsStorageFolderName() + "/"
  return if (fileSpec.startsWith(osPrefix)) StringUtil.trimStart(fileSpec, osPrefix) else fileSpec
}

private fun getCategory(componentClasses: List<Class<PersistentStateComponent<Any>>>): SettingsCategory {
  when {
    componentClasses.isEmpty() -> return SettingsCategory.OTHER
    componentClasses.size == 1 -> return ComponentCategorizer.getCategory(componentClasses[0])
    else -> {
      componentClasses.forEach {
        val category = ComponentCategorizer.getCategory(it)
        if (category != SettingsCategory.OTHER) {
          // Once found, ignore any other possibly conflicting definitions
          return category
        }
      }
      return SettingsCategory.OTHER
    }
  }
}

private fun getSchemeCategory(fileSpec: String): SettingsCategory? {
  val separatorIndex = fileSpec.indexOf("/")
  if (separatorIndex >= 0) {
    when (fileSpec.substring(0, separatorIndex)) {
      CodeStyleSchemesImpl.CODE_STYLES_DIR_PATH -> return SettingsCategory.CODE
      EditorColorsManagerImpl.FILE_SPEC -> return SettingsCategory.UI
      KEYMAPS_DIR_PATH -> return SettingsCategory.KEYMAP
      InspectionProfileManager.INSPECTION_DIR -> return SettingsCategory.CODE
    }
  }
  return null
}

private fun getSubCategory(componentClasses: List<Class<PersistentStateComponent<Any>>>): String? {
  for (componentClass in componentClasses) {
    if (AppEditorFontOptions::class.java.isAssignableFrom(componentClass)) {
      return SettingsSyncUiGroup.EDITOR_FONT_ID
    }
  }
  return null
}

private fun findComponentClasses(fileSpec: String): List<Class<PersistentStateComponent<Any>>> {
  val componentManager = ApplicationManager.getApplication() as ComponentManagerImpl
  val componentClasses = ArrayList<Class<PersistentStateComponent<Any>>>()
  componentManager.processAllImplementationClasses { aClass, _ ->
    if (PersistentStateComponent::class.java.isAssignableFrom(aClass)) {
      val state = aClass.getAnnotation(State::class.java)
      state?.storages?.forEach { storage ->
        if (!storage.deprecated && storage.value == fileSpec) {
          @Suppress("UNCHECKED_CAST")
          componentClasses.add(aClass as Class<PersistentStateComponent<Any>>)
        }
      }
    }
  }
  return componentClasses
}