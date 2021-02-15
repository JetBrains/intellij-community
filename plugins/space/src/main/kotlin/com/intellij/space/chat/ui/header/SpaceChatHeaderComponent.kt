// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui.header

import com.intellij.space.chat.model.api.SpaceChatHeaderDetails
import com.intellij.space.chat.model.impl.SpaceChatReviewHeaderDetails
import libraries.coroutines.extra.Lifetime
import javax.swing.JComponent

internal fun SpaceChatHeaderDetails.createComponent(lifetime: Lifetime): JComponent = when (this) {
  is SpaceChatReviewHeaderDetails -> SpaceChatReviewHeaderComponent(lifetime, this)
  else -> throw IllegalArgumentException("This chat header type is not supported")
}