// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.actions

import com.intellij.openapi.actionSystem.ActionPlaces

object MarkdownActionPlaces {
  @JvmStatic
  val INSERT_POPUP
    get() = ActionPlaces.getPopupPlace("MarkdownInsertPopup")
}
