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
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.util.StringBuilderSpinAllocator;

public class ToggleCaseAction extends EditorAction {
  public ToggleCaseAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      if (!editor.getSelectionModel().hasSelection()) {
        editor.getSelectionModel().selectWordAtCaret(true);
      }

      int startOffset = editor.getSelectionModel().getSelectionStart();
      int endOffset = editor.getSelectionModel().getSelectionEnd();

      StringBuilder builder = StringBuilderSpinAllocator.alloc();
      try {
        final String text = editor.getDocument().getCharsSequence().subSequence(startOffset, endOffset).toString();
        toCase(builder, text, true);
        if (text.equals(builder.toString())) {
          toCase(builder, text, false);
        }
        editor.getDocument().replaceString(startOffset, endOffset, builder.toString());
        editor.getSelectionModel().setSelection(startOffset, endOffset);
      }
      finally {
        StringBuilderSpinAllocator.dispose(builder);
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
