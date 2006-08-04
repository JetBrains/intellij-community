package com.intellij.openapi.diff.impl.external;

import com.intellij.openapi.diff.*;
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.diff.impl.FrameWrapper;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.Nullable;

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

  @Nullable
  private static DiffPanelImpl createDiffPanelIfShouldShow(DiffRequest request, Window window) {
    DiffPanelImpl diffPanel = (DiffPanelImpl)DiffManagerImpl.createDiffPanel(request, window);
    if (checkNoDifferenceAndNotify(diffPanel, request, window)) {
      diffPanel.dispose();
      diffPanel = null;
    }
    return diffPanel;
  }

  private static void showDiffDialog(DialogBuilder builder, Collection hints) {
    builder.showModal(!hints.contains(DiffTool.HINT_SHOW_NOT_MODAL_DIALOG));
  }

  private static boolean shouldOpenDialog(Collection hints) {
    if (hints.contains(DiffTool.HINT_SHOW_MODAL_DIALOG)) return true;
    if (hints.contains(DiffTool.HINT_SHOW_NOT_MODAL_DIALOG)) return true;
    if (hints.contains(DiffTool.HINT_SHOW_FRAME)) return false;
    return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() instanceof JDialog;
  }

  private static boolean checkNoDifferenceAndNotify(DiffPanel diffPanel, DiffRequest data, final Window window) {
    if (!diffPanel.hasDifferences()) {
      DiffManagerImpl manager = (DiffManagerImpl) DiffManager.getInstance();
      if (!Comparing.equal(manager.getComparisonPolicy(), ComparisonPolicy.DEFAULT)) {
        ComparisonPolicy oldPolicy = manager.getComparisonPolicy();
        manager.setComparisonPolicy(ComparisonPolicy.DEFAULT);
        DiffPanel maybeDiffPanel = DiffManagerImpl.createDiffPanel(data, window);
        manager.setComparisonPolicy(oldPolicy);

        boolean hasDiffs = maybeDiffPanel.hasDifferences();
        maybeDiffPanel.dispose();

        if (hasDiffs) return false;
      }

      return !askForceOpenDiff(data);
    }
    return false;
  }

  private static boolean askForceOpenDiff(DiffRequest data) {
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
      message = DiffBundle.message("diff.contents.are.identical.message.text");
    else
      message = DiffBundle.message("diff.contents.have.differences.only.in.line.separators.message.text");
    Messages.showInfoMessage(data.getProject(), message, DiffBundle.message("no.differences.dialog.title"));
    return false;
    //return Messages.showDialog(data.getProject(), message + "\nShow diff anyway?", "No Differences", new String[]{"Yes", "No"}, 1,
    //                    Messages.getQuestionIcon()) == 0;
  }

  public boolean canShow(DiffRequest data) {
    DiffContent[] contents = data.getContents();
    if (contents.length != 2) return false;
    for (DiffContent content : contents) {
      if (content.isBinary()) return false;
      VirtualFile file = content.getFile();
      if (file != null && file.isDirectory()) return false;
    }
    return true;
  }
}
