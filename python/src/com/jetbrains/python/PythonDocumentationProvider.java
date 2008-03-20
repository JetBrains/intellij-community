package com.jetbrains.python;

import com.intellij.lang.documentation.QuickDocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.xml.util.XmlStringUtil;
import com.jetbrains.python.psi.PyDocStringOwner;

/**
 * @author yole
 */
public class PythonDocumentationProvider extends QuickDocumentationProvider {
  public String getQuickNavigateInfo(final PsiElement element) {
    return null;
  }

  public String generateDoc(final PsiElement element, final PsiElement originalElement) {
    if (element instanceof PyDocStringOwner) {
      String docString = ((PyDocStringOwner) element).getDocString();
      return XmlStringUtil.escapeString(docString).replace("\n", "<br>");
    }
    return null;
  }
}
