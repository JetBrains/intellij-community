package com.intellij.openapi.diff.impl.mergeTool;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.diff.impl.FrameWrapper;
import com.intellij.openapi.diff.impl.incrementalMerge.ui.MergePanel2;
import com.intellij.openapi.ui.DialogBuilder;

public class MergeTool implements DiffTool {
  public void show(DiffRequest data) {
    if (data instanceof MergeRequest) {
      showDialog((MergeRequestImpl)data);
      return;
    }
    FrameWrapper frameWrapper = new FrameWrapper(data.getGroupKey());
    DiffViewer mergePanel = createMergeComponent(data);
    frameWrapper.setComponent(mergePanel.getComponent());
    frameWrapper.setPreferredFocusedComponent(mergePanel.getPreferredFocusedComponent());
    frameWrapper.closeOnEsc();
    frameWrapper.setTitle(data.getWindowTitle());
    frameWrapper.setData(DataConstants.PROJECT, data.getProject());
    frameWrapper.show();
  }

  private MergePanel2 createMergeComponent(DiffRequest data) {
    MergePanel2 mergePanel = new MergePanel2();
    mergePanel.setDiffRequest(data);
    return mergePanel;
  }

  private void showDialog(MergeRequestImpl data) {
    DialogBuilder builder = new DialogBuilder(data.getProject());
    builder.setDimensionServiceKey(data.getGroupKey());
    builder.setTitle(data.getWindowTitle());
    MergePanel2 mergePanel = createMergeComponent(data);
    builder.setCenterPanel(mergePanel.getComponent());
    builder.setPreferedFocusComponent(mergePanel.getPreferredFocusedComponent());
    data.setActions(builder, mergePanel);
    builder.setHelpId(data.getHelpId());
    int result = builder.show();
    data.setResult(result);
  }

  public boolean canShow(DiffRequest data) {
    DiffContent[] contents = data.getContents();
    if (contents.length != 3) return false;
    for (int i = 0; i < contents.length; i++) {
      DiffContent content = contents[i];
      if (content.getDocument() == null) return false;
    }
    return true;
  }
}
