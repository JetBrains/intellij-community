// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import circlet.client.api.GitCommitInfo

object SpaceReviewCommit {
  fun GitCommitInfo.subject(): String = this.message.trimEnd('\n').substringBefore("\n\n")
}