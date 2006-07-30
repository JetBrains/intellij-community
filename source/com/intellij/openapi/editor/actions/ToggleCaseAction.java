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
        editor.getSelectionModel().selectWordAtCaret(false);
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
        final String finalText = builder.toString();
        editor.getDocument().replaceString(startOffset, endOffset, finalText);
        editor.getSelectionModel().setSelection(startOffset, startOffset + finalText.length());
      }
      finally {
        StringBuilderSpinAllocator.dispose(builder);
      }
    }

    private static void toCase(final StringBuilder builder, final String text, final boolean lower ) {
      builder.setLength(0);
      if (isIdentifier(text)) {
        if (lower) {
          if (!isUnderscored(text)) {
            builder.append(text);
            return;
          }

          boolean prevIsUnderscore = false;
          for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '_' && i > 0) {
              prevIsUnderscore = true;
              continue;
            }

            if (prevIsUnderscore) {
              builder.append(Character.toUpperCase(c));
              prevIsUnderscore = false;
            }
            else {
              builder.append(Character.toLowerCase(c));
            }
          }
        }
        else {
          if (!isCamelCased(text)) {
            builder.append(text);
            return;
          }

          boolean prevWasLower = false;
          for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isUpperCase(c)) {
              if (prevWasLower) {
                builder.append('_');
                prevWasLower = false;
              }
            }
            else if (Character.isLowerCase(c)) {
              prevWasLower = true;
            }
            builder.append(Character.toUpperCase(c));
          }
        }
      }
      else {
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

    private static boolean isUnderscored(String text) {
      for( int i = 0; i < text.length(); ++i) {
        char c = text.charAt(i);
        if (c != '_' && !Character.isDigit(c) && !Character.isUpperCase(c)) return false;
      }
      return true;
    }

    private static boolean isCamelCased(String text) {
      return text.indexOf('_') <= 0;
    }

    private static boolean isIdentifier(String text) {
      if (!Character.isLetter(text.charAt(0))) return false;
      for (int i = 0; i < text.length(); i++) {
        if (!Character.isJavaIdentifierPart(text.charAt(i))) return false;
      }

      return true;
    }
  }
}
