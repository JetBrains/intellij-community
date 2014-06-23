package com.intellij.tokenindex;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.util.containers.HashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class RecursiveTokenizingVisitor extends PsiRecursiveElementWalkingVisitor {
  private final int myBaseOffset;
  private final List<Token> myTokens;
  private final Set<String> myLanguages = new HashSet<String>();
  private final Set<Language> myAcceptableLanguages;

  private Language myLastLanguage;
  private StructuralSearchProfile myLastProfile;

  public RecursiveTokenizingVisitor(List<Token> tokens, Set<Language> acceptableLanguages, int baseOffset) {
    super(true);
    myTokens = tokens;
    myAcceptableLanguages = acceptableLanguages;
    myBaseOffset = baseOffset;
  }

  public RecursiveTokenizingVisitor(List<Token> tokens, Set<Language> acceptableLanguages) {
    this(tokens, acceptableLanguages, 0);
  }

  public RecursiveTokenizingVisitor() {
    this(new ArrayList<Token>(), null);
  }

  public List<Token> getTokens() {
    return myTokens;
  }

  public void addToken(Token token) {
    myTokens.add(token);
  }

  public Set<String> getLanguages() {
    return myLanguages;
  }

  @Override
  public void visitElement(PsiElement element) {
    Language language = element.getLanguage();
    if (language != myLastLanguage) {
      myLastLanguage = language;
      myLastProfile = StructuralSearchUtil.getProfileByPsiElement(element);
    }
    if (myLastProfile != null) {
      language = myLastProfile.getLanguage(element);
    }
    if (myAcceptableLanguages == null || myAcceptableLanguages.contains(language)) {
      Tokenizer tokenizer = StructuralSearchUtil.getTokenizerForLanguage(language);
      if (tokenizer != null) {
        myLanguages.add(language.getID());
        if (!tokenizer.visit(element, this)) {
          return;
        }
      }
    }
    super.visitElement(element);
  }

  @Override
  protected void elementFinished(PsiElement element) {
    Tokenizer tokenizer = StructuralSearchUtil.getTokenizerForLanguage(element.getLanguage());
    if (tokenizer != null) {
      tokenizer.elementFinished(element, this);
    }
  }

  public int getBaseOffset() {
    return myBaseOffset;
  }
}
