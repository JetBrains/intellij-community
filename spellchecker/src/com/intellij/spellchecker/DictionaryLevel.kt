// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.spellchecker.dictionary.EditableDictionary
import com.intellij.spellchecker.dictionary.ProjectDictionary
import com.intellij.spellchecker.settings.DictionaryLayerChangesListener
import com.intellij.spellchecker.settings.DictionaryLayersChangesDispatcher
import com.intellij.spellchecker.state.AppDictionaryState
import com.intellij.spellchecker.state.ProjectDictionaryState
import com.intellij.spellchecker.util.SpellCheckerBundle
import org.jetbrains.annotations.Nls
import java.util.concurrent.ConcurrentMap

interface DictionaryLayersProvider {
  fun getLayers(project: Project): List<DictionaryLayer>
  fun startWatchingChanges(project: Project) { }

  companion object {
    val EP_NAME = ExtensionPointName.create<DictionaryLayersProvider>("com.intellij.spellchecker.dictionaryLayersProvider")
    @JvmStatic
    fun getAllLayers(project: Project): Collection<DictionaryLayer> {
      return project.service<PerProjectDictionaryLayersHolder>().getAllLayers()
    }

    @JvmStatic
    fun getLayer(project: Project, layerName: String): DictionaryLayer? {
      return project.service<PerProjectDictionaryLayersHolder>().getLayer(layerName)
    }
  }
}

@Service(Service.Level.PROJECT)
class PerProjectDictionaryLayersHolder(private val project: Project) {
  private var layersMap: Map<String, DictionaryLayer> = mapOf()

  init {
    project.service<DictionaryLayersChangesDispatcher>()
      .register(object : DictionaryLayerChangesListener {
        override fun layersChanged() {
          rebuild()
        }
      })
    DictionaryLayersProvider.EP_NAME.extensionList.forEach{
      it.startWatchingChanges(project)
    }
    rebuild()
  }

  fun rebuild() {
    layersMap = DictionaryLayersProvider.EP_NAME.extensionList
      .flatMap { it.getLayers(project) }
      .associateBy { it.name }
  }

  fun getLayer(layerName: String): DictionaryLayer? {
    return layersMap[layerName]
  }

  fun getAllLayers(): Collection<DictionaryLayer> {
    return layersMap.values
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
    val name = SpellCheckerBundle.message("dictionary.name.project.level")
  }

  override val name = Companion.name
  override val dictionary: ProjectDictionary = project.service<ProjectDictionaryState>().projectDictionary
}

object ApplicationDictionaryLayer : DictionaryLayer {
  override val name = SpellCheckerBundle.message("dictionary.name.application.level")
  override val dictionary: EditableDictionary by lazy { AppDictionaryState.getInstance().dictionary }
}