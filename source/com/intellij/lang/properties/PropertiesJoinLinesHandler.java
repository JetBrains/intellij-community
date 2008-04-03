package com.intellij.lang.properties;

import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;

public class PropertiesJoinLinesHandler implements JoinLinesHandlerDelegate {
  public int tryJoinLines(final Document doc, final PsiFile psiFile, int start, final int end) {
    if (!(psiFile instanceof PropertiesFile)) return -1;
    // strip continuation char
    if (PropertiesUtil.isUnescapedBackSlashAtTheEnd(doc.getText().substring(0, start + 1))) {
      doc.deleteString(start, start + 1);
      start--;
    }
    return start + 1;
  }
}
