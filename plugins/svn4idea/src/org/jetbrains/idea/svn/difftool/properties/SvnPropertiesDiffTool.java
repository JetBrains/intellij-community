package org.jetbrains.idea.svn.difftool.properties;

import com.intellij.openapi.util.diff.api.FrameDiffTool;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import org.jetbrains.annotations.NotNull;

public class SvnPropertiesDiffTool implements FrameDiffTool {
  @NotNull
  @Override
  public String getName() {
    return "Svn Properties Viewer";
  }

  @Override
  public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return request instanceof SvnPropertiesDiffRequest;
  }

  @NotNull
  @Override
  public DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return new SvnPropertiesDiffViewer(context, (SvnPropertiesDiffRequest)request);
  }
}
