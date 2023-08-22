// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.difftool

import com.intellij.diff.util.DiffPlaces
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.components.*
import com.intellij.openapi.util.Key
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.XMap
import java.util.*

@State(name = "SvnDiffSettings", storages = [(Storage(value = DiffUtil.DIFF_CONFIG))], category = SettingsCategory.TOOLS)
class SvnDiffSettingsHolder : PersistentStateComponent<SvnDiffSettingsHolder.State> {
  data class PlaceSettings(
    var SPLITTER_PROPORTION: Float = 0.9f,
    var HIDE_PROPERTIES: Boolean = false
  )

  class SvnDiffSettings internal constructor(private val PLACE_SETTINGS: PlaceSettings) {
    constructor() : this(PlaceSettings())

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
    return SvnDiffSettings(placeSettings)
  }

  private fun copyStateWithoutDefaults(): State {
    val result = State()
    result.PLACES_MAP = DiffUtil.trimDefaultValues(myState.PLACES_MAP, { defaultPlaceSettings(it) })
    return result
  }

  private fun defaultPlaceSettings(place: String): PlaceSettings {
    return PlaceSettings()
  }


  class State {
    @XMap
    @OptionTag
    @JvmField
    var PLACES_MAP: TreeMap<String, PlaceSettings> = TreeMap()
  }

  private var myState: State = State()

  override fun getState(): State {
    return copyStateWithoutDefaults()
  }

  override fun loadState(state: State) {
    myState = state
  }
}
