package com.intellij.settingsSync

import com.intellij.configurationStore.getPerOsSettingsStorageFolderName
import com.intellij.configurationStore.schemeManager.SchemeManagerFactoryBase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.settingsSync.config.EDITOR_FONT_SUBCATEGORY_ID

internal fun isSyncEnabled(fileSpec: String, roamingType: RoamingType): Boolean {
  if (roamingType == RoamingType.DISABLED) return false
  val rawFileSpec = removeOsPrefix(fileSpec)
  if (rawFileSpec == SettingsSyncSettings.FILE_SPEC) return true
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

private val categoryCache = mutableMapOf<String, SettingsCategory>()
fun getCategory(fileName: String): SettingsCategory {
  categoryCache[fileName]?.let { cachedCategory ->
    return cachedCategory
  }

  val componentClasses = findComponentClasses(fileName)
  val category = getSchemeCategory(fileName) ?: getCategory(componentClasses)

  categoryCache[fileName] = category

  return category
}

private fun getSchemeCategory(fileSpec: String): SettingsCategory? {
  // fileSpec is e.g. keymaps/mykeymap.xml
  val separatorIndex = fileSpec.indexOf("/")
  val directoryName = if (separatorIndex >= 0) fileSpec.substring(0, separatorIndex) else fileSpec  // e.g. 'keymaps'

  var settingsCategory: SettingsCategory? = null
  (SchemeManagerFactory.getInstance() as SchemeManagerFactoryBase).process {
    if (it.fileSpec == directoryName) {
      settingsCategory = it.getSettingsCategory()
    }
  }
  return settingsCategory
}

fun getFileSpec(path: String): String {
  return removeOsPrefix(path)
}

private fun getSubCategory(componentClasses: List<Class<PersistentStateComponent<Any>>>): String? {
  for (componentClass in componentClasses) {
    if (AppEditorFontOptions::class.java.isAssignableFrom(componentClass)) {
      return EDITOR_FONT_SUBCATEGORY_ID
    }
  }
  return null
}

private fun findComponentClasses(fileSpec: String): List<Class<PersistentStateComponent<Any>>> {
  val componentManager = ApplicationManager.getApplication() as ComponentManagerImpl
  val componentClasses = ArrayList<Class<PersistentStateComponent<Any>>>()
  componentManager.processAllImplementationClasses { aClass, _ ->
    if (PersistentStateComponent::class.java.isAssignableFrom(aClass)) {
      val state = aClass.getAnnotation(State::class.java) ?: return@processAllImplementationClasses
      if (state.additionalExportDirectory.isNotEmpty() && (fileSpec == state.additionalExportDirectory || fileSpec.startsWith(state.additionalExportDirectory + "/")) ||
          state.storages.any { storage -> !storage.deprecated && storage.value == fileSpec }) {
        @Suppress("UNCHECKED_CAST")
        componentClasses.add(aClass as Class<PersistentStateComponent<Any>>)
      }
    }
  }
  return componentClasses
}