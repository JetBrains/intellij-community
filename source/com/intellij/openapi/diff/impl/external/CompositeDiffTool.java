package com.intellij.openapi.diff.impl.external;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.DiffTool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

class CompositeDiffTool implements DiffTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.external.CompositeDiffTool");
  private final List<DiffTool> myTools;

  public CompositeDiffTool(List<DiffTool> tools) {
    myTools = new ArrayList<DiffTool>(tools);
  }

  public void show(DiffRequest data) {
    checkDiffData(data);
    DiffTool tool = chooseTool(data);
    if (tool != null) tool.show(data);
    else LOG.error("Can't show");
  }

  public boolean canShow(DiffRequest data) {
    checkDiffData(data);
    return chooseTool(data) != null;
  }

  @Nullable
  private DiffTool chooseTool(DiffRequest data) {
    for (DiffTool tool : myTools) {
      if (tool.canShow(data)) return tool;
    }
    return null;
  }

  private static void checkDiffData(@NotNull DiffRequest data) {
    DiffContent[] contents = data.getContents();
    for (DiffContent content : contents) {
      LOG.assertTrue(content != null);
    }
  }
}
