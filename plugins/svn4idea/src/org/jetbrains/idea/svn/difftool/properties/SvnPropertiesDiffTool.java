// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.difftool.properties;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.requests.DiffRequest;
import org.jetbrains.annotations.NotNull;

import static org.jetbrains.idea.svn.SvnBundle.message;

public class SvnPropertiesDiffTool implements FrameDiffTool {
  @Override
  public @NotNull String getName() {
    return message("svn.properties.viewer");
  }

  @Override
  public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return request instanceof SvnPropertiesDiffRequest;
  }

  @Override
  public @NotNull DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return SvnPropertiesDiffViewer.create(context, (SvnPropertiesDiffRequest)request);
  }
}
