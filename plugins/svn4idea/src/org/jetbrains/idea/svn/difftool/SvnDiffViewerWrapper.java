package org.jetbrains.idea.svn.difftool;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool.DiffViewer;
import com.intellij.diff.impl.DiffViewerWrapper;
import com.intellij.diff.requests.DiffRequest;
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
