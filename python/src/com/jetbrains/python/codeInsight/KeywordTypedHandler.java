package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyStringLiteralExpression;

import java.util.Arrays;

/**
 * Handles overtyping ':' in definitions.
 * User: dcheryasov
 * Date: May 29, 2009 4:42:03 AM
 */
public class KeywordTypedHandler extends TypedHandlerDelegate {

  private static char[] ourInterestingChars;
  static {
    ourInterestingChars = new char[]{':',};
    Arrays.sort(ourInterestingChars);
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
      final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      final int offset = editor.getCaretModel().getOffset();

      PsiElement token = file.findElementAt(offset - 1);
      if (token == null || offset >= document.getTextLength()) return Result.CONTINUE; // sanity check: beyond EOL

      PsiElement here_elt = file.findElementAt(offset);
      if (here_elt == null) return Result.CONTINUE; 
      if (here_elt instanceof PyStringLiteralExpression || here_elt.getParent() instanceof PyStringLiteralExpression) return Result.CONTINUE;

      if (character == ':') {
        // double colons aren't found in Pyhton's syntax, so we can safely overtype a colon everywhere but strings.
        String here_text = here_elt.getText();
        if (":".equals(here_text)) {
          documentManager.commitDocument(document);
          editor.getCaretModel().moveToOffset(offset + 1); // overtype, that is, jump over
          return Result.STOP;
        }
      }
    }

    return Result.CONTINUE; // the default
  }

}
