// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.mergeinfo;

import icons.SvnIcons;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public enum ListMergeStatus {
  COMMON(SvnIcons.Common),
  MERGED(SvnIcons.Integrated),
  NOT_MERGED(SvnIcons.Notintegrated),
  ALIEN(null),
  REFRESHING(SvnIcons.IntegrationStatusUnknown);

  @Nullable
  private final Icon myIcon;

  ListMergeStatus(@Nullable final Icon icon) {
    myIcon = icon;
  }

  @Nullable
  public Icon getIcon() {
    return myIcon;
  }

  @Contract(value = "null -> null; !null -> !null", pure = true)
  public static ListMergeStatus from(@Nullable MergeCheckResult mergeCheckResult) {
    ListMergeStatus result = null;

    if (mergeCheckResult != null) {
      switch (mergeCheckResult) {
        case MERGED:
          result = MERGED;
          break;
        case COMMON:
          result = COMMON;
          break;
        default:
          result = NOT_MERGED;
      }
    }

    return result;
  }
}
