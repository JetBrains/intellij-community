/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 14, 2002
 * Time: 6:29:03 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;

public class CutLineEndAction extends EditorAction {
  public CutLineEndAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      final Document doc = editor.getDocument();
      if (doc.getLineCount() == 0) return;
      int caretOffset = editor.getCaretModel().getOffset();
      int lineEndOffset = doc.getLineEndOffset(doc.getLineNumber(caretOffset));

      if (caretOffset >= lineEndOffset) return;

      copyToClipboard(doc, caretOffset, lineEndOffset, dataContext, editor);

      doc.deleteString(caretOffset, lineEndOffset);
    }

    private void copyToClipboard(final Document doc,
                                 int caretOffset,
                                 int lineEndOffset,
                                 DataContext dataContext,
                                 Editor editor) {
      String s = doc.getCharsSequence().subSequence(caretOffset, lineEndOffset).toString();

      s = StringUtil.convertLineSeparators(s, "\n");
      StringSelection contents = new StringSelection(s);

      Project project = DataKeys.PROJECT.getData(dataContext);
      if (project == null) {
        Clipboard clipboard = editor.getComponent().getToolkit().getSystemClipboard();
        clipboard.setContents(contents, defaultClipboardOwner);
      }
      else {
        CopyPasteManager.getInstance().setContents(contents);
      }
    }
  }

  private static class ClipboardObserver implements ClipboardOwner {
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }
  }

  private static ClipboardOwner defaultClipboardOwner = new ClipboardObserver();
}
