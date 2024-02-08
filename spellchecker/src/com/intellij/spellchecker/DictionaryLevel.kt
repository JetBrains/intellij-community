// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker

import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.spellchecker.dictionary.EditableDictionary
import com.intellij.spellchecker.dictionary.ProjectDictionary
import com.intellij.spellchecker.state.AppDictionaryState
import com.intellij.spellchecker.state.ProjectDictionaryState
import com.intellij.spellchecker.util.SpellCheckerBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
interface DictionaryLayersProvider {
  fun getLayers(project: Project): List<DictionaryLayer>

  companion object {
    val EP_NAME = ExtensionPointName.create<DictionaryLayersProvider>("com.intellij.spellchecker.dictionaryLayersProvider")
    @JvmStatic
    fun getAllLayers(project: Project): Collection<DictionaryLayer> {
      return EP_NAME.extensionList.flatMap { it.getLayers(project) }
    }

    @JvmStatic
    fun getLayer(project: Project, layerName: String): DictionaryLayer? {
      return EP_NAME.extensionList.flatMap { it.getLayers(project) }.firstOrNull { it.name == layerName }
    }
  }
}

interface DictionaryLayer {
  val dictionary: EditableDictionary
  val name: @Nls String
}

class PlatformSettingsDictionaryLayersProvider : DictionaryLayersProvider {
  override fun getLayers(project: Project): List<DictionaryLayer> {
    return listOf(ApplicationDictionaryLayer, ProjectDictionaryLayer(project))
  }
}

class ProjectDictionaryLayer(val project: Project) : DictionaryLayer {
  companion object {
    val name = SpellCheckerBundle.messagePointer("dictionary.name.project.level")
  }

  override val name: String
    get() = Companion.name.get()
  override val dictionary: ProjectDictionary
    get() = project.service<ProjectDictionaryState>().projectDictionary
}

object ApplicationDictionaryLayer : DictionaryLayer {
  override val name: String
    get() = SpellCheckerBundle.message("dictionary.name.application.level")
  override val dictionary: EditableDictionary
    get() = AppDictionaryState.getInstance().dictionary
}