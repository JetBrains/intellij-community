/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 20, 2002
 * Time: 4:13:37 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.util.StringBuilderSpinAllocator;

public class ToggleCaseAction extends TextComponentEditorAction {
  public ToggleCaseAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      final SelectionModel selectionModel = editor.getSelectionModel();

      final int[] starts;
      final int[] ends;
      LogicalPosition blockStart = null;
      LogicalPosition blockEnd = null;

      if (selectionModel.hasBlockSelection()) {
        starts = selectionModel.getBlockSelectionStarts();
        ends = selectionModel.getBlockSelectionEnds();
        blockStart = selectionModel.getBlockStart();
        blockEnd = selectionModel.getBlockEnd();
      }
      else {
        if (!selectionModel.hasSelection()) {
          selectionModel.selectWordAtCaret(true);
        }

        starts = new int[] {selectionModel.getSelectionStart()};
        ends = new int[] {selectionModel.getSelectionEnd()};
      }

      selectionModel.removeBlockSelection();
      selectionModel.removeSelection();

      for (int i = 0; i < starts.length; i++) {
        int startOffset = starts[i];
        int endOffset = ends[i];
        StringBuilder builder = StringBuilderSpinAllocator.alloc();
        try {
          final String text = editor.getDocument().getCharsSequence().subSequence(startOffset, endOffset).toString();
          toCase(builder, text, true);
          if (text.equals(builder.toString())) {
            toCase(builder, text, false);
          }
          editor.getDocument().replaceString(startOffset, endOffset, builder.toString());

        }
        finally {
          StringBuilderSpinAllocator.dispose(builder);
        }
      }

      if (blockStart != null) {
        selectionModel.setBlockSelection(blockStart, blockEnd);
      }
      else {
        selectionModel.setSelection(starts[0], ends[0]);
      }
    }

    private static void toCase(final StringBuilder builder, final String text, final boolean lower ) {
      builder.setLength(0);
      boolean prevIsSlash = false;
      for( int i = 0; i < text.length(); ++i) {
        char c = text.charAt(i);
        if( !prevIsSlash ) {
          c = (lower) ? Character.toLowerCase(c) : Character.toUpperCase(c);
        }
        prevIsSlash = c == '\\';
        builder.append(c);
      }
    }
  }
}
