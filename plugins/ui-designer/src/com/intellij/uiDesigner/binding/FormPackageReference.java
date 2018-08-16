// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.binding;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class FormPackageReference extends ReferenceInForm {
  protected FormPackageReference(final PsiPlainTextFile file, TextRange range) {
    super(file, range);
  }

  @Override
  public PsiElement resolve() {
    final Project project = myFile.getProject();
    String text = getRangeText().replace('/', '.');
    return JavaPsiFacade.getInstance(project).findPackage(text);
  }

  @Override
  public boolean isReferenceTo(@NotNull final PsiElement element) {
    if (!(element instanceof PsiPackage)) {
      return false;
    }
    final String qName = ((PsiPackage)element).getQualifiedName().replace('.', '/');
    final String rangeText = getRangeText();
    return qName.equals(rangeText);
  }

  @Override
  public PsiElement handleElementRename(@NotNull final String newElementName) {
    final String s = getRangeText();
    int pos = s.lastIndexOf("/");
    if (pos < 0) {
      updateRangeText(newElementName);
    }
    else {
      updateRangeText(s.substring(0, pos+1) + newElementName);
    }
    return myFile;
  }

  @Override
  public PsiElement bindToElement(@NotNull final PsiElement element) throws IncorrectOperationException {
    return myFile;
  }
}
