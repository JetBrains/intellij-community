// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui.thread

import javax.swing.JComponent

internal interface SpaceChatThreadActionsFactory {
  fun createActionsComponent(): JComponent
}