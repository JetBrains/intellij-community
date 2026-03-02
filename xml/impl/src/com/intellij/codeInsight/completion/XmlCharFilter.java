// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.editorActions.XmlAutoPopupHandler;
import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.application.RuntimeFlagsKt;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;

public class XmlCharFilter extends CharFilter {

  public static boolean isInXmlContext(Lookup lookup) {
    if (!lookup.isCompletion()) return false;

    if (RuntimeFlagsKt.isEditorLockFreeTypingEnabled()) {
      // TODO: rework for lock-free typing, getContainingFile requires RA on EDT
      return false;
    }

    PsiElement psiElement = lookup.getPsiElement();
    PsiFile file = lookup.getPsiFile();
    if (!(file instanceof XmlFile) && psiElement != null) {
      file = psiElement.getContainingFile();
    }


    if (file instanceof XmlFile) {
      if (psiElement != null) {
        PsiElement elementToTest = psiElement;
        if (elementToTest instanceof PsiWhiteSpace) {
          elementToTest = elementToTest.getParent(); // JSPX has whitespace with language Java
        }

        final Language language = elementToTest.getLanguage();
        if (!(language instanceof XMLLanguage)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  public static boolean isWithinTag(Lookup lookup) {
    if (isInXmlContext(lookup)) {
      PsiElement psiElement = lookup.getPsiElement();
      final PsiElement parentElement = psiElement != null ? psiElement.getParent() : null;
      if (parentElement instanceof XmlTag) return true;
      if (parentElement instanceof PsiErrorElement && parentElement.getParent() instanceof XmlDocument) return true;

      return (parentElement instanceof XmlDocument || parentElement instanceof XmlText) &&
             (psiElement.textMatches("<") || psiElement.textMatches("\""));
    }
    return false;
  }

  @Override
  public Result acceptChar(char c, int prefixLength, @NotNull Lookup lookup) {
    if (!isInXmlContext(lookup)) return null;

    if (Character.isJavaIdentifierPart(c)) return Result.ADD_TO_PREFIX;
    switch(c){
      case '-':
      case ':':
      case '?':
        return Result.ADD_TO_PREFIX;
      case '/':
        if (isWithinTag(lookup)) {
          if (prefixLength > 0) {
            return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
          }
          XmlAutoPopupHandler.autoPopupXmlLookup(lookup.getProject(), lookup.getEditor());
          return Result.HIDE_LOOKUP;
        }
        return Result.ADD_TO_PREFIX;

      case '>': if (prefixLength > 0) {
        return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
      }

      case '\'':
      case '\"':
        return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
      default:
        return null;
    }
  }
}
