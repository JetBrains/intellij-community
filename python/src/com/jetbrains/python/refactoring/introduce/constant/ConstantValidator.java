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
package com.jetbrains.python.refactoring.introduce.constant;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.refactoring.introduce.IntroduceValidator;

/**
 * @author Alexey.Ivanov
 */
public class ConstantValidator extends IntroduceValidator {
  public String check(String name, PsiElement psiElement) {
    if (isDefinedInScope(name, psiElement) || isDefinedInScope(name, psiElement.getContainingFile())) {
      return PyBundle.message("refactoring.introduce.constant.scope.error");
    }
    return null;
  }
}
