package com.intellij.codeInsight.completion;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.PsiPlainText;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.ide.highlighter.custom.SyntaxTable;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Oct 14, 2004
 * Time: 4:28:39 PM
 * To change this template use File | Settings | File Templates.
 */
public class SyntaxTableCompletionData extends WordCompletionData {
  private SyntaxTable mySyntaxTable;

  public SyntaxTableCompletionData(SyntaxTable _syntaxTable) {
    mySyntaxTable = _syntaxTable;
    mySyntaxTable.getKeywords1();

    final CompletionVariant variant = new CompletionVariant(TrueFilter.INSTANCE);
    variant.includeScopeClass(PsiPlainText.class, true);
    variant.includeScopeClass(XmlToken.class, true); // for embedding custom file types into xml
    variant.addCompletionFilterOnElement(TrueFilter.INSTANCE);
    final String[] empty = {};

    variant.addCompletion((String[])mySyntaxTable.getKeywords1().toArray(empty));
    variant.addCompletion((String[])mySyntaxTable.getKeywords2().toArray(empty));
    variant.addCompletion((String[])mySyntaxTable.getKeywords3().toArray(empty));
    variant.addCompletion((String[])mySyntaxTable.getKeywords4().toArray(empty));
    variant.setInsertHandler(ourWordInsertHandler);

    registerVariant(variant);
  }

  public String findPrefix(PsiElement insertedElement, int offset) {
    String text = insertedElement.getText();
    int offsetInElement = offset - insertedElement.getTextOffset();
    int start = offsetInElement - 1;
    while(start >=0 ) {
      if(!Character.isJavaIdentifierStart(text.charAt(start))) break;
      --start;
    }
    return text.substring(start+1, offsetInElement).trim();
  }
}
