package com.intellij.structuralsearch.plugin.replace.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil;
import com.intellij.structuralsearch.impl.matcher.PatternTreeContext;

/**
 * @author Eugene.Kudelevsky
 */
public class ReplacerUtil {
  private ReplacerUtil() {
  }

  public static PsiElement[] createTreeForReplacement(String replacement, PatternTreeContext treeContext, ReplacementContext context) {
    FileType fileType = context.getOptions().getMatchOptions().getFileType();
    return MatcherImplUtil.createTreeFromText(replacement, treeContext, fileType, context.getProject());
  }

  public static PsiElement copySpacesAndCommentsBefore(PsiElement elementToReplace,
                                                          PsiElement[] patternElements,
                                                          String replacementToMake,
                                                          PsiElement elementParent) {
    int i = 0;
    while (true) {    // if it goes out of bounds then deep error happens
      if (!(patternElements[i] instanceof PsiComment || patternElements[i] instanceof PsiWhiteSpace)) {
        break;
      }
      ++i;
      if (patternElements.length == i) {
        break;
      }
    }

    if (patternElements.length == i) {
      Logger logger = Logger.getInstance(StructuralSearchProfile.class.getName());
      logger.error("Unexpected replacement structure:" + replacementToMake);
    }

    if (i != 0) {
      elementParent.addRangeBefore(patternElements[0], patternElements[i - 1], elementToReplace);
    }
    return patternElements[i];
  }
}
