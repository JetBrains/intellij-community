// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn;

import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class SvnFormatSelector {

  public static @NotNull WorkingCopyFormat findRootAndGetFormat(@NotNull File path) {
    File root = SvnUtil.getWorkingCopyRoot(path);

    return root != null ? SvnUtil.getFormat(root) : WorkingCopyFormat.UNKNOWN;
  }
}
