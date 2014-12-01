package org.jetbrains.idea.svn.difftool;

import com.intellij.openapi.util.diff.api.FrameDiffTool.DiffContext;
import com.intellij.openapi.util.diff.api.FrameDiffTool.DiffViewer;
import com.intellij.openapi.util.diff.impl.DiffViewerWrapper;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import org.jetbrains.annotations.NotNull;

public class SvnDiffViewerWrapper implements DiffViewerWrapper {
  @NotNull private final DiffRequest myPropertyRequest;

  public SvnDiffViewerWrapper(@NotNull DiffRequest propertyRequest) {
    myPropertyRequest = propertyRequest;
  }

  @Override
  public DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request, @NotNull DiffViewer wrappedViewer) {
    return new SvnDiffViewer(context, myPropertyRequest, wrappedViewer);
  }
}
