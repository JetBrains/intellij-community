// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.spellchecker.dictionary.EditableDictionary
import com.intellij.spellchecker.dictionary.ProjectDictionary
import com.intellij.spellchecker.settings.DictionaryLayerChangesSubscriber
import com.intellij.spellchecker.settings.DictionaryLayersChangesDispatcher
import com.intellij.spellchecker.state.AppDictionaryState
import com.intellij.spellchecker.state.ProjectDictionaryState
import com.intellij.spellchecker.util.SpellCheckerBundle
import org.jetbrains.annotations.Nls

abstract class DictionaryLayersProvider {
  abstract fun getLayers(project: Project): List<DictionaryLayer>
  open fun startWatchingChanges(project: Project) { }

  companion object {
    val EP_NAME = ExtensionPointName.create<DictionaryLayersProvider>("com.intellij.spellchecker.dictionaryLayersProvider")
    fun getAllLayers(project: Project): List<DictionaryLayer> {
      return project.service<PerProjectDictionaryLayersHolder>().getAllLayers()
    }

    fun getLayer(project: Project, layerName: String): DictionaryLayer? {
      return project.service<PerProjectDictionaryLayersHolder>().getLayer(layerName)
    }
  }
}

@Service(Service.Level.PROJECT)
class PerProjectDictionaryLayersHolder(private val project: Project) {
  private lateinit var layersMap: Map<String, DictionaryLayer>
  private lateinit var layers: List<DictionaryLayer>

  init {
    project.service<DictionaryLayersChangesDispatcher>()
      .register(object : DictionaryLayerChangesSubscriber {
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
    layers = DictionaryLayersProvider.EP_NAME.extensionList.flatMap { it.getLayers(project) }.toList()
    layersMap = layers.associateBy { it.name }
  }

  fun getLayer(layerName: String): DictionaryLayer? {
    return layersMap[layerName]
  }

  fun getAllLayers(): List<DictionaryLayer> {
    return layers
  }
}

interface DictionaryLayer {
  val dictionary: EditableDictionary
  @get:Nls
  val name: String
}

class PlatformSettingsDictionaryLayersProvider : DictionaryLayersProvider() {
  override fun getLayers(project: Project): List<DictionaryLayer> {
    return listOf(ApplicationDictionaryLayer.INSTANCE, ProjectDictionaryLayer(project))
  }
}

class ProjectDictionaryLayer(val project: Project) : DictionaryLayer {
  companion object {
    val name = SpellCheckerBundle.message("dictionary.name.project.level")
  }

  override val name = Companion.name
  override val dictionary: ProjectDictionary = project.service<ProjectDictionaryState>().projectDictionary
}

class ApplicationDictionaryLayer : DictionaryLayer {
  companion object {
    val name = SpellCheckerBundle.message("dictionary.name.application.level")
    val INSTANCE = ApplicationDictionaryLayer()
  }

  override val name = Companion.name
  override val dictionary: EditableDictionary = AppDictionaryState.getInstance().dictionary
}