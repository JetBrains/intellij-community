package org.jetbrains.idea.maven.dom.code;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.idea.maven.dom.MavenDomUtil;

public class MavenTypedHandlerDelegate extends TypedHandlerDelegate {
  @Override
  public Result charTyped(char c, Project project, Editor editor, PsiFile file) {
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) return Result.CONTINUE;

    if (c != '{') return Result.CONTINUE;
    if (!shouldProcess(file)) return Result.CONTINUE;

    int offset = editor.getCaretModel().getOffset();
    if (shouldCloseBrace(editor, offset, c)) {
      editor.getDocument().insertString(offset, "}");
      return Result.STOP;
    }
    return Result.CONTINUE;
  }

  private boolean shouldCloseBrace(Editor editor, int offset, char c) {
    CharSequence text = editor.getDocument().getCharsSequence();

    if (offset < 2) return false;
    if (c != '{' || text.charAt(offset - 2) != '$') return false;

    if (offset < text.length()) {
      char next = text.charAt(offset);
      if (next == '}') return false;
      if (Character.isLetterOrDigit(next)) return false;
    }

    return true;
  }

  public static boolean shouldProcess(PsiFile file) {
    return MavenDomUtil.isMavenFile(file) || MavenDomUtil.isFiltererResourceFile(file);
  }
}
