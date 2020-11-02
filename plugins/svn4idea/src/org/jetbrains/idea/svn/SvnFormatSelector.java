// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class SvnFormatSelector {

  @NotNull
  public static WorkingCopyFormat findRootAndGetFormat(@NotNull File path) {
    File root = SvnUtil.getWorkingCopyRoot(path);

    return root != null ? SvnUtil.getFormat(root) : WorkingCopyFormat.UNKNOWN;
  }
}
