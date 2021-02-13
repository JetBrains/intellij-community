// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui.discussion

import circlet.code.api.CodeDiscussionRecord
import circlet.platform.client.KCircletClient
import com.intellij.space.chat.ui.thread.SpaceChatThreadActionsFactory
import libraries.coroutines.extra.Lifetime
import runtime.reactive.Property
import javax.swing.JComponent

internal class SpaceChatDiscussionActionsFactory(
  private val lifetime: Lifetime,
  private val client: KCircletClient,
  private val discussion: Property<CodeDiscussionRecord>
) : SpaceChatThreadActionsFactory {
  override fun createActionsComponent(): JComponent = createResolveComponent(lifetime, client, discussion)
}