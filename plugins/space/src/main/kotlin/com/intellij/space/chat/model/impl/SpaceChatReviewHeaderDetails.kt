// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.model.impl

import circlet.code.api.CodeReviewState
import com.intellij.openapi.util.NlsSafe
import com.intellij.space.chat.model.api.SpaceChatHeaderDetails
import com.intellij.space.vcs.SpaceProjectInfo
import org.jetbrains.annotations.Nls
import runtime.reactive.Property

internal class SpaceChatReviewHeaderDetails(
  val spaceProject: SpaceProjectInfo,
  val state: Property<CodeReviewState>,
  @Nls val title: Property<String>,
  @NlsSafe val reviewKey: String?,
) : SpaceChatHeaderDetails