/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.refactoring.introduce.field;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.refactoring.introduce.IntroduceValidator;

/**
 * @author Dennis.Ushakov
 */
public class IntroduceFieldValidator extends IntroduceValidator {
  @Override
  public String check(String name, PsiElement psiElement) {
    PyClass containingClass = PsiTreeUtil.getParentOfType(psiElement, PyClass.class);
    if (containingClass == null) {
      return null;
    }
    if (containingClass.findInstanceAttribute(name, true) != null) {
      return PyPsiBundle.message("refactoring.introduce.constant.scope.error");
    }
    return null;
  }
}
