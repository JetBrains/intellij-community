package org.jetbrains.idea.svn.difftool.properties;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.requests.DiffRequest;
import org.jetbrains.annotations.NotNull;

public class SvnPropertiesDiffTool implements FrameDiffTool {
  @NotNull
  @Override
  public String getName() {
    return "SVN properties viewer";
  }

  @Override
  public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return request instanceof SvnPropertiesDiffRequest;
  }

  @NotNull
  @Override
  public DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return SvnPropertiesDiffViewer.create(context, (SvnPropertiesDiffRequest)request);
  }
}
