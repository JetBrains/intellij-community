// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.actions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import icons.SpaceIcons

object SpaceActionUtils {
  fun showIconInActionSearch(e: AnActionEvent) {
    e.presentation.icon = if (e.place == ActionPlaces.ACTION_SEARCH) SpaceIcons.Main else null
  }
}
