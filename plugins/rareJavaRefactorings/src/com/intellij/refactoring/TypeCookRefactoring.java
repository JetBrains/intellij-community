// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.psi.PsiElement;

import java.util.List;

public interface TypeCookRefactoring extends Refactoring {
  List<PsiElement> getElements();
}
