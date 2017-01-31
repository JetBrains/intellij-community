/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.difftool

import com.intellij.diff.util.DiffPlaces
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Key
import com.intellij.util.xmlb.annotations.MapAnnotation
import java.util.*


@State(
  name = "SvnDiffSettings",
  storages = arrayOf(Storage(value = DiffUtil.DIFF_CONFIG))
)
class SvnDiffSettingsHolder : PersistentStateComponent<SvnDiffSettingsHolder.State> {
  class SharedSettings(
  )

  data class PlaceSettings(
    var SPLITTER_PROPORTION: Float = 0.9f,
    var HIDE_PROPERTIES: Boolean = false
  )

  class SvnDiffSettings internal constructor(private val SHARED_SETTINGS: SharedSettings,
                                             private val PLACE_SETTINGS: PlaceSettings) {
    constructor() : this(SharedSettings(), PlaceSettings())

    var isHideProperties: Boolean
      get()      = PLACE_SETTINGS.HIDE_PROPERTIES
      set(value) { PLACE_SETTINGS.HIDE_PROPERTIES = value }

    var splitterProportion: Float
      get()      = PLACE_SETTINGS.SPLITTER_PROPORTION
      set(value) { PLACE_SETTINGS.SPLITTER_PROPORTION = value }

    companion object {
      @JvmField val KEY: Key<SvnDiffSettings> = Key.create("SvnDiffSettings")

      @JvmStatic fun getSettings(): SvnDiffSettings = getSettings(null)
      @JvmStatic fun getSettings(place: String?): SvnDiffSettings = service<SvnDiffSettingsHolder>().getSettings(place)
    }
  }

  fun getSettings(place: String?): SvnDiffSettings {
    val placeKey = place ?: DiffPlaces.DEFAULT
    val placeSettings = myState.PLACES_MAP.getOrPut(placeKey, { defaultPlaceSettings(placeKey) })
    return SvnDiffSettings(myState.SHARED_SETTINGS, placeSettings)
  }

  private fun copyStateWithoutDefaults(): State {
    val result = State()
    result.SHARED_SETTINGS = myState.SHARED_SETTINGS
    result.PLACES_MAP = DiffUtil.trimDefaultValues(myState.PLACES_MAP, { defaultPlaceSettings(it) })
    return result
  }

  private fun defaultPlaceSettings(place: String): PlaceSettings {
    return PlaceSettings()
  }


  class State {
    @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
    var PLACES_MAP: TreeMap<String, PlaceSettings> = TreeMap()
    var SHARED_SETTINGS = SharedSettings()
  }

  private var myState: State = State()

  override fun getState(): State {
    return copyStateWithoutDefaults()
  }

  override fun loadState(state: State) {
    myState = state
  }
}
