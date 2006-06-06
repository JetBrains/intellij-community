
package com.intellij.psi.impl;

import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiLock;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNameHelper;

public class PsiNameHelperImpl extends PsiNameHelper{
  private final PsiManager myManager;
  private Lexer myLexer;
  private LanguageLevel myLastLanguageLevel = LanguageLevel.JDK_1_3;

  public PsiNameHelperImpl(PsiManager manager) {
    myManager = manager;
    myLastLanguageLevel = LanguageLevel.JDK_1_3; // to be updated by updateLexer()
    myLexer = new JavaLexer(myLastLanguageLevel);
  }

  private void updateLexer(LanguageLevel languageLevel){
    if (!myLastLanguageLevel.equals(languageLevel)){
      myLastLanguageLevel = languageLevel;
      myLexer = new JavaLexer(myLastLanguageLevel);
    }
  }

  public boolean isIdentifier(String text) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (text == null) return false;

    synchronized (PsiLock.LOCK) {
      updateLexer(myManager.getEffectiveLanguageLevel());
      myLexer.start(text.toCharArray());
      if (myLexer.getTokenType() != JavaTokenType.IDENTIFIER) return false;
      myLexer.advance();
      return myLexer.getTokenType() == null;
    }
  }

  public boolean isIdentifier(String text, LanguageLevel languageLevel) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    synchronized (PsiLock.LOCK) {
      updateLexer(languageLevel);
      myLexer.start(text.toCharArray());
      if (myLexer.getTokenType() != JavaTokenType.IDENTIFIER) return false;
      myLexer.advance();
      return myLexer.getTokenType() == null;
    }
  }

  public boolean isKeyword(String text) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    synchronized (PsiLock.LOCK) {
      updateLexer(myManager.getEffectiveLanguageLevel());
      myLexer.start(text.toCharArray());
      if (myLexer.getTokenType() == null || !JavaTokenType.KEYWORD_BIT_SET.contains(myLexer.getTokenType())) return false;
      myLexer.advance();
      return myLexer.getTokenType() == null;
    }
  }

  public boolean isQualifiedName(String text){
    int index = 0;
    while(true){
      int index1 = text.indexOf('.', index);
      if (index1 < 0) index1 = text.length();
      if (!isIdentifier(text.substring(index, index1))) return false;
      if (index1 == text.length()) return true;
      index = index1 + 1;
    }
  }
}