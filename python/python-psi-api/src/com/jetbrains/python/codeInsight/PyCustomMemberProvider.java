// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.Function;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.types.PyType;

public abstract class PyCustomMemberProvider {
  public static PyCustomMemberProvider getInstance() {
    return ApplicationManager.getApplication().getService(PyCustomMemberProvider.class);
  }

  public abstract boolean isReferenceToMe(PsiReference reference, PyCustomMember member);

  public abstract PyTypedElement createPyCustomMemberTarget(PyCustomMember member,
                                                            PyClass clazz,
                                                            PsiElement context,
                                                            PsiElement resolveTarget,
                                                            Function<? super PsiElement, ? extends PyType> typeCallback,
                                                            PyCustomMemberTypeInfo<?> customTypeInfo, boolean resolveToInstance);
}
