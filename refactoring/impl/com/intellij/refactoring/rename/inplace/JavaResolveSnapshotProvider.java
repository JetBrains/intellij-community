package com.intellij.refactoring.rename.inplace;

import com.intellij.psi.PsiElement;

/**
 * User: Maxim.Mossienko
 * Date: 29.07.2009
 * Time: 14:07:20
 */
public class JavaResolveSnapshotProvider extends ResolveSnapshotProvider {
  @Override
  public ResolveSnapshot createSnapshot(PsiElement scope) {
    return new JavaResolveSnapshot(scope);
  }
}
