package com.intellij.settingsSync

import com.intellij.configurationStore.getPerOsSettingsStorageFolderName
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions
import com.intellij.openapi.util.text.StringUtil
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.settingsSync.config.SettingsSyncUiGroup

internal fun isSyncEnabled(fileSpec: String, roamingType: RoamingType): Boolean {
  val rawFileSpec = removeOsPrefix(fileSpec)
  val componentClasses = findComponentClasses(rawFileSpec)
  val category = getSchemeCategory(rawFileSpec) ?: getCategory(componentClasses)
  if (category != SettingsCategory.OTHER && SettingsSyncSettings.getInstance().isCategoryEnabled(category)) {
    val subCategory = getSubCategory(componentClasses)
    if (subCategory != null) {
      return SettingsSyncSettings.getInstance().isSubcategoryEnabled(category, subCategory)
    }
    return roamingType != RoamingType.DISABLED
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

@Suppress("SpellCheckingInspection")
private fun getSchemeCategory(fileSpec: String): SettingsCategory? {
  val separatorIndex = fileSpec.indexOf("/")
  if (separatorIndex >= 0) {
    when (fileSpec.substring(0, separatorIndex)) {
      "codestyles" -> return SettingsCategory.CODE
      "colors" -> return SettingsCategory.UI
      "keymaps" -> return SettingsCategory.KEYMAP
      "inspection" -> return SettingsCategory.CODE
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
        @Suppress("DEPRECATION")
        if (!storage.deprecated && (storage.file == fileSpec || storage.value == fileSpec)) {
          @Suppress("UNCHECKED_CAST")
          componentClasses.add(aClass as Class<PersistentStateComponent<Any>>)
        }
      }
    }
  }
  return componentClasses
}