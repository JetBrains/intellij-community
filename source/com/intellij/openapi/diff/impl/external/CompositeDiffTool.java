package com.intellij.openapi.diff.impl.external;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.DiffTool;

import java.util.ArrayList;
import java.util.Iterator;
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

  private DiffTool chooseTool(DiffRequest data) {
    for (Iterator<DiffTool> iterator = myTools.iterator(); iterator.hasNext();) {
      DiffTool tool = iterator.next();
      if (tool.canShow(data)) return tool;
    }
    return null;
  }

  private void checkDiffData(DiffRequest data) {
    LOG.assertTrue(data != null);
    DiffContent[] contents = data.getContents();
    for (int i = 0; i < contents.length; i++) {
      DiffContent content = contents[i];
      LOG.assertTrue(content != null);
    }
  }
}
