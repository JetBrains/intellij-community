/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 11.10.2006
 * Time: 17:53:16
 */
package com.intellij.uiDesigner.binding;

import org.jetbrains.annotations.Nullable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.util.TextRange;

public class FormEnumConstantReference extends ReferenceInForm {
  private PsiClassType myEnumClass;

  protected FormEnumConstantReference(final PsiPlainTextFile file, final TextRange range, final PsiClassType enumClass) {
    super(file, range);
    myEnumClass = enumClass;
  }

  @Nullable
  public PsiElement resolve() {
    PsiClass enumClass = myEnumClass.resolve();
    if (enumClass == null) return null;
    return enumClass.findFieldByName(getRangeText(), false);
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }
}