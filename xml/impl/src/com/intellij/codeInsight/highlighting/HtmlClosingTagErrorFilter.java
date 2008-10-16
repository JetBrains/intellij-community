package com.intellij.codeInsight.highlighting;

import com.intellij.lang.html.HTMLLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class HtmlClosingTagErrorFilter extends HighlightErrorFilter {

  public boolean shouldHighlightErrorElement(@NotNull final PsiErrorElement element) {
    final PsiFile psiFile = element.getContainingFile();
    if (psiFile == null || (psiFile.getViewProvider().getBaseLanguage() != HTMLLanguage.INSTANCE
                            && HTMLLanguage.INSTANCE != element.getLanguage())) return true;

    final PsiElement[] children = element.getChildren();
    if (children.length > 0) {
      if (children[0] instanceof XmlToken && XmlTokenType.XML_END_TAG_START == ((XmlToken)children[0]).getTokenType()) {
        if (XmlErrorMessages.message("xml.parsing.closing.tag.mathes.nothing").equals(element.getErrorDescription())) {
          return false;
        }
      }
    }

    return true;
  }
}
