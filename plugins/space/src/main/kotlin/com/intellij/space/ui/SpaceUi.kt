// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.labels.LinkLabel

internal object SpaceUi {
  fun linkLabel(@NlsContexts.LinkLabel text: String, url: String): LinkLabel<*> = linkLabel(text) {
    BrowserUtil.browse(url)
  }

  fun linkLabel(@NlsContexts.LinkLabel text: String, action: () -> Unit): LinkLabel<*> {
    return LinkLabel.create(text, action)
  }
}