// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff

import com.intellij.openapi.project.Project
import com.intellij.util.ui.codereview.diff.MutableDiffRequestChainProcessor

internal class SpaceDiffRequestProcessor(project: Project) : MutableDiffRequestChainProcessor(project, null) {
  override fun getFastLoadingTimeMillis(): Int {
    return 10
  }
}