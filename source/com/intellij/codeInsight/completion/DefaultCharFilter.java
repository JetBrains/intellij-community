/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 23, 2002
 * Time: 3:01:22 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;

public class DefaultCharFilter implements CharFilter {
  private final PsiFile myFile;
  private CharFilter myDelegate = null;

  public DefaultCharFilter(PsiFile file) {
    myFile = file;

    if (myFile instanceof XmlFile) {
      myDelegate = new XmlCharFilter();
    }
  }

  public int accept(char c) {
    if (myDelegate != null) return myDelegate.accept(c);

    if (Character.isJavaIdentifierPart(c)) return CharFilter.ADD_TO_PREFIX;
    switch(c){
      case '.':
      case ',':
      case ';':
      case '=':
      case ' ':
      case ':':
      case '(':
        return CharFilter.SELECT_ITEM_AND_FINISH_LOOKUP;

      default:
        return CharFilter.HIDE_LOOKUP;
    }
  }
}
