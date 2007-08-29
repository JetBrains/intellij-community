
package com.intellij.psi.impl;

import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNameHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiNameHelperImpl extends PsiNameHelper{
  private final PsiManager myManager;
  private Lexer myLexer;
  private LanguageLevel myLastLanguageLevel = LanguageLevel.JDK_1_3;
  private final Object LOCK = new Object();

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

  public boolean isIdentifier(@Nullable String text) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (text == null) return false;

    synchronized (LOCK) {
      updateLexer(myManager.getEffectiveLanguageLevel());
      myLexer.start(text,0,text.length(),0);
      if (myLexer.getTokenType() != JavaTokenType.IDENTIFIER) return false;
      myLexer.advance();
      return myLexer.getTokenType() == null;
    }
  }

  public boolean isIdentifier(@Nullable String text, @NotNull LanguageLevel languageLevel) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (text == null) return false;

    synchronized (LOCK) {
      updateLexer(languageLevel);
      myLexer.start(text,0,text.length(),0);
      if (myLexer.getTokenType() != JavaTokenType.IDENTIFIER) return false;
      myLexer.advance();
      return myLexer.getTokenType() == null;
    }
  }

  public boolean isKeyword(@Nullable String text) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (text == null) return false;

    synchronized (LOCK) {
      updateLexer(myManager.getEffectiveLanguageLevel());
      myLexer.start(text,0,text.length(),0);
      if (myLexer.getTokenType() == null || !JavaTokenType.KEYWORD_BIT_SET.contains(myLexer.getTokenType())) return false;
      myLexer.advance();
      return myLexer.getTokenType() == null;
    }
  }

  public boolean isQualifiedName(@Nullable String text){
    if (text == null) return false;
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