package com.intellij.openapi.diff.impl.external;

import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffPanel;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.DiffTool;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.diff.impl.FrameWrapper;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

// Author: dyoma

class FrameDiffTool implements DiffTool {
  public void show(DiffRequest request) {
    Collection hints = request.getHints();
    boolean shouldOpenDialog = shouldOpenDialog(hints);
    if (shouldOpenDialog) {
      DialogBuilder builder = new DialogBuilder(request.getProject());
      DiffPanelImpl diffPanel = createDiffPanelIfShouldShow(request, builder.getWindow());
      if (diffPanel == null) return;
      builder.setCenterPanel(diffPanel.getComponent());
      builder.addDisposable(diffPanel);
      builder.setPreferedFocusComponent(diffPanel.getPreferredFocusedComponent());
      builder.removeAllActions();
      builder.setTitle(request.getWindowTitle());
      builder.setDimensionServiceKey(request.getGroupKey());
      showDiffDialog(builder, hints);
    } else {
      FrameWrapper frameWrapper = new FrameWrapper(request.getGroupKey());
      DiffPanelImpl diffPanel = createDiffPanelIfShouldShow(request, frameWrapper.getFrame());
      if (diffPanel == null) return;
      frameWrapper.setTitle(request.getWindowTitle());
      DiffUtil.initDiffFrame(frameWrapper, diffPanel);
      frameWrapper.show();
    }
  }

  private DiffPanelImpl createDiffPanelIfShouldShow(DiffRequest request, Window window) {
    DiffPanelImpl diffPanel = (DiffPanelImpl)DiffManagerImpl.createDiffPanel(request, window);
    if (checkNoDifferenceAndNotify(diffPanel, request)) {
      diffPanel.dispose();
      diffPanel = null;
    }
    return diffPanel;
  }

  private void showDiffDialog(DialogBuilder builder, Collection hints) {
    builder.showModal(!hints.contains(DiffTool.HINT_SHOW_NOT_MODAL_DIALOG));
  }

  private boolean shouldOpenDialog(Collection hints) {
    if (hints.contains(DiffTool.HINT_SHOW_MODAL_DIALOG)) return true;
    if (hints.contains(DiffTool.HINT_SHOW_NOT_MODAL_DIALOG)) return true;
    if (hints.contains(DiffTool.HINT_SHOW_FRAME)) return false;
    return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() instanceof JDialog;
  }

  private boolean checkNoDifferenceAndNotify(DiffPanel diffPanel, DiffRequest data) {
    if (!diffPanel.hasDifferences()) {
      return !askForceOpenDiff(data);
    }
    return false;
  }

  private boolean askForceOpenDiff(DiffRequest data) {
    String title1 = data.getContentTitles()[0];
    String title2 = data.getContentTitles()[1];
    byte[] bytes1;
    byte[] bytes2;
    try {
      bytes1 = data.getContents()[0].getBytes();
      bytes2 = data.getContents()[1].getBytes();
    }
    catch (IOException e) {
      MessagesEx.error(data.getProject(), e.getMessage()).showNow();
      return false;
    }
    String message;
    if (Arrays.equals(bytes1, bytes2))
      message = title1 + " and " + title2 + " are identical";
    else
      message = title1 + " and " + title2 + " have differences only in line separators";
    Messages.showInfoMessage(data.getProject(), message, "No Differences");
    return false;
    //return Messages.showDialog(data.getProject(), message + "\nShow diff anyway?", "No Differences", new String[]{"Yes", "No"}, 1,
    //                    Messages.getQuestionIcon()) == 0;
  }

  public boolean canShow(DiffRequest data) {
    DiffContent[] contents = data.getContents();
    if (contents.length != 2) return false;
    for (int i = 0; i < contents.length; i++) {
      DiffContent content = contents[i];
      if (content.isBinary()) return false;
      VirtualFile file = content.getFile();
      if (file != null && file.isDirectory()) return false;
    }
    return true;
  }
}
