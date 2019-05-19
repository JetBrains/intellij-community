// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.mergeinfo

import com.intellij.icons.AllIcons
import icons.SvnIcons
import javax.swing.Icon

enum class ListMergeStatus(val icon: Icon?) {
  COMMON(SvnIcons.Common),
  MERGED(SvnIcons.Integrated),
  NOT_MERGED(SvnIcons.Notintegrated),
  ALIEN(null),
  REFRESHING(AllIcons.Nodes.Unknown);

  companion object {
    @JvmStatic
    fun from(mergeCheckResult: MergeCheckResult?): ListMergeStatus? = when (mergeCheckResult) {
      MergeCheckResult.MERGED -> MERGED
      MergeCheckResult.COMMON -> COMMON
      null -> null
      else -> NOT_MERGED
    }
  }
}
