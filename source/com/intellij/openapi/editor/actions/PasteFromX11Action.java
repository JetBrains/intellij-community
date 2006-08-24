package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.SystemInfo;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

/**
 * Author: msk
 */
public class PasteFromX11Action extends EditorAction {
  public PasteFromX11Action() {
    super(new Handler());
  }

  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    DataContext dataContext = e.getDataContext();
    Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    if (editor == null || !SystemInfo.X11PasteEnabledSystem) {
      presentation.setEnabled(false);
    }
    else {
      boolean rightPlace = true;
      final InputEvent inputEvent = e.getInputEvent();
      if (inputEvent instanceof MouseEvent) {
        rightPlace = editor.getMouseEventArea((MouseEvent)inputEvent) == EditorMouseEventArea.EDITING_AREA;
      }
      presentation.setEnabled(rightPlace);
    }
  }

  public static class Handler extends EditorWriteActionHandler {
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      final Clipboard clip = editor.getComponent().getToolkit().getSystemSelection();
      if (clip != null) {
        final Transferable res = clip.getContents(null);
        editor.putUserData(EditorEx.LAST_PASTED_REGION, EditorModificationUtil.pasteFromTransferrable(res, editor));
      }
    }
  }
}

