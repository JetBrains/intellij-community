// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui

import com.intellij.util.ui.JBValue

internal enum class SpaceChatAvatarType(val size: JBValue) {
  MAIN_CHAT(JBValue.UIInteger("space.chat.avatar.size", 30)),
  THREAD(JBValue.UIInteger("space.chat.thread.avatar.size", 20))
}