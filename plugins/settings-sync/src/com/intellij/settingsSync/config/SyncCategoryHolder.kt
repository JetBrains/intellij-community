package com.intellij.settingsSync.config

import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SettingsCategory.*
import com.intellij.settingsSync.SettingsSyncState
import com.intellij.settingsSync.SettingsSyncBundle.message
import org.jetbrains.annotations.Nls
import java.util.*

internal class SyncCategoryHolder(
  val descriptor: Category,
  private val state: SettingsSyncState
) {
  var isSynchronized: Boolean = state.isCategoryEnabled(descriptor.category)

  val name: @Nls String
    get() = descriptor.name

  val description: @Nls String
    get() = descriptor.description

  val secondaryGroup: SyncSubcategoryGroup?
    get() = descriptor.secondaryGroup

  fun reset() {
    with(descriptor) {
      isSynchronized = state.isCategoryEnabled(category)
      if (secondaryGroup != null) {
        secondaryGroup.getDescriptors().forEach {
          it.isSelected = isSynchronized && state.isSubcategoryEnabled(category, it.id)
        }
      }
    }
  }

  fun apply() {
    with(descriptor) {
      if (secondaryGroup != null) {
        secondaryGroup.getDescriptors().forEach {
          // !isSynchronized not store disabled states individually
          state.setSubcategoryEnabled(category, it.id, !isSynchronized || it.isSelected)
        }
      }
      state.setCategoryEnabled(category, isSynchronized)
    }
  }

  fun isModified(): Boolean {
    with(descriptor) {
      if (isSynchronized != state.isCategoryEnabled(category)) return true
      if (secondaryGroup != null && isSynchronized) {
        secondaryGroup.getDescriptors().forEach {
          if (it.isSelected != state.isSubcategoryEnabled(category, it.id)) return true
        }
      }
      return false
    }
  }

  companion object {
    fun createAllForState(state: SettingsSyncState): List<SyncCategoryHolder> {
      val retval = arrayListOf<SyncCategoryHolder>()
      for (descriptor in Category.DESCRIPTORS) {
        retval.add(SyncCategoryHolder(descriptor, state))
      }
      return retval
    }
  }

  internal class Category(
    val category: SettingsCategory,
    val secondaryGroup: SyncSubcategoryGroup? = null
  ) {

    val name: @Nls String
      get() {
        return message("${categoryKey}.name")
      }

    val description: @Nls String
      get() {
        return message("${categoryKey}.description")
      }

    private val categoryKey: String
      get() {
        return "settings.category." + category.name.lowercase(Locale.getDefault())
      }

    companion object {
      internal val DESCRIPTORS: List<Category> = listOf(
        Category(UI, SyncUiGroup()),
        Category(KEYMAP),
        Category(CODE),
        Category(PLUGINS, SyncPluginsGroup()),
        Category(TOOLS),
        Category(SYSTEM),
      )
    }
  }

}