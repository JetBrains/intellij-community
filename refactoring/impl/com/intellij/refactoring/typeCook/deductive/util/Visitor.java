package com.intellij.refactoring.typeCook.deductive.util;

import com.intellij.psi.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Jul 5, 2004
 * Time: 6:44:49 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class Visitor extends PsiRecursiveElementVisitor {
  @Override public void visitPackage(final PsiPackage aPackage) {
    final PsiDirectory[] dirs = aPackage.getDirectories();

    for (int i = 0; i < dirs.length; i++) {
      final PsiFile[] files = dirs[i].getFiles();

      for (int j = 0; j < files.length; j++) {
        final PsiFile file = files[j];

        if (file instanceof PsiJavaFile) {
          super.visitJavaFile(((PsiJavaFile)file));
        }
      }
    }
  }
}