package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PythonFileType;

import java.util.Arrays;

/**
 * Handles special quoting requirements:
 * <ul>
 * <li>Do not type closing quote near potential quotable piece; only type it before space, comma, closing brackets;</li>
 * <li>Handle triple quotes conveniently.</li>
 * </ul>
 * User: dcheryasov
 * Date: Feb 1, 2010 6:31:31 PM
 */
public class QuoteTypedHandler extends TypedHandlerDelegate {

  private static char[] ourInterestingChars; // we handle typing of these
  private static char[] ourAutoClosingChars; // we add auto-close quotes before these
  static {
    ourInterestingChars = new char[]{'"', '\''};
    Arrays.sort(ourInterestingChars);

    ourAutoClosingChars = new char[]{'}', ']', ')', ',', ':', ';', ' ', '\t', '\n'};
    Arrays.sort(ourAutoClosingChars);
  }

  private static boolean isInteresting(char c) {
    return Arrays.binarySearch(ourInterestingChars, c) >= 0; // arrays don't have a contains() method, thus this 'efficient search'.
  }

  // snatched from VelocityTypedHandler
  static void typeInStringAndMoveCaret(Editor editor, int offset, String str) {
      EditorModificationUtil.typeInStringAtCaretHonorBlockSelection(editor, str, true);
      editor.getCaretModel().moveToOffset(offset);
  }

  @Override
  public Result beforeCharTyped(char character, Project project, Editor editor, PsiFile file, FileType fileType) {
    if (!(fileType instanceof PythonFileType)) return Result.CONTINUE; // else we'd mess up with other file types!
    if (isInteresting(character)) {
      final Document document = editor.getDocument();
      final int offset = editor.getCaretModel().getOffset();
      CharSequence chars = document.getCharsSequence();
      if (offset > document.getTextLength()) return Result.CONTINUE; // sanity check: beyond EOL

      if (inAutoClosingContext(chars, offset)) {
        if (isAfterTripleQuote(chars, offset, character)) {
          // close triple quoted string and stand inside it
          typeInStringAndMoveCaret(editor, offset, new StringBuilder().append(character).append(character).append(character).toString());
          return Result.STOP;
        }
        else {
          if (offset >= 1 && chars.charAt(offset-1) == character) {
            if (inOpenTripleQuotedLiteral(chars, offset, character)) {
              // second quote of the same type; auto-close triple-quoted literal
              typeInStringAndMoveCaret(editor, offset+2, new StringBuilder().append(character).append(character).toString());
              return Result.STOP;
            }
            else {
              // after just another quote of the same sort: don't auto-close
              typeInStringAndMoveCaret(editor, offset+1, String.valueOf(character));
              return Result.STOP;
            }
          }
          else  {
            // close plain string as the standard handler sees fit
            return Result.CONTINUE;
          }
        }
      }
      else { // don't auto-close
        // when user wants to type triple quote, he prints a quote, it is auto-closed; print third quote after them
        if (insideEmptyQuotes(chars, offset, character)) {
          typeInStringAndMoveCaret(editor, offset+2, String.valueOf(character));
        }
        else {
          // just type it and prevent default handling
          typeInStringAndMoveCaret(editor, offset+1, String.valueOf(character));
        }
        return Result.STOP;
      }
    }

    return Result.CONTINUE; // the default
  }

  private static boolean inOpenTripleQuotedLiteral(CharSequence text, int offset, char quote) {
    // scan back until we're sure we crossed an opening triple quote. in most cases, we scan back to the BOF.
    int quote_count = 0;
    int pos = offset;
    while (pos > 0) {
      char c = text.charAt(pos);
      if (c != quote) quote_count = 0;
      else {
        quote_count += 1;
        if (quote_count == 3) {
          if (isNotEscaped(text, pos - 1)) return true;
          else quote_count = 0;
        }
      }
      pos -= 1;
    }
    return false;
  }

  private static boolean isNotEscaped(CharSequence text, int offset) {
    // scan back and see if the number of consequent backslashes is even.
    int pos = offset;
    int escape_count = 0;
    pos -= 1;
    while (pos >= 0) {
      if (text.charAt(pos) == '\\') {
        escape_count += 1;
        pos -= 1;
      }
      else break;
    }
    return (escape_count % 2 == 0);
  }

  private static boolean insideEmptyQuotes(CharSequence chars, int offset, char character) {
    if (offset >= 1 && offset < chars.length()) {
      boolean result = chars.charAt(offset-1) == character && chars.charAt(offset) == character;
      result &= offset < 2 || chars.charAt(offset-2) != '\\';
      return result;
    }
    return false;

  }

  private static boolean isAfterTripleQuote(CharSequence chars, int offset, char character) {
    if (offset >= 3) {
      return (
        isNotEscaped(chars, offset-4) &&
        chars.charAt(offset-1) == character &&
        chars.charAt(offset-2) == character &&
        chars.charAt(offset-3) == character
      );
    }
    return false;
  }

  private static boolean inAutoClosingContext(CharSequence chars, int offset) {
    return offset < chars.length() && Arrays.binarySearch(ourAutoClosingChars, chars.charAt(offset)) >=0;
  }

}
