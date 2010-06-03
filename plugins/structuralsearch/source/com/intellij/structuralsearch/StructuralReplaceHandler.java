package com.intellij.structuralsearch;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.plugin.replace.impl.ReplacementInfoImpl;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class StructuralReplaceHandler {
  public abstract void replace(final ReplacementInfoImpl info,
                               final PsiElement elementToReplace,
                               String replacementToMake,
                               final PsiElement elementParent);

  public void prepare(ReplacementInfoImpl info, Project project) {
  }
}
