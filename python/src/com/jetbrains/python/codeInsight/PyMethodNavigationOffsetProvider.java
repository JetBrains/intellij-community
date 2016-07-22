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
package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.navigation.MethodNavigationOffsetProvider;
import com.intellij.codeInsight.navigation.MethodUpDownUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;

import java.util.ArrayList;
import java.util.Collections;

/**
 * User : ktisha
 */
public class PyMethodNavigationOffsetProvider implements MethodNavigationOffsetProvider {

  @Override
  public int[] getMethodNavigationOffsets(final PsiFile file, final int caretOffset) {
    if (file instanceof PyFile) {
      final ArrayList<PsiElement> array = new ArrayList<>();
      addNavigationElements(array, file);
      return MethodUpDownUtil.offsetsFromElements(array);
    }
    return null;
  }

  private static void addNavigationElements(final ArrayList<PsiElement> array, final PsiElement psiElement) {
    if (psiElement instanceof PyFile) {
      for (PyClass pyClass : ((PyFile)psiElement).getTopLevelClasses()) {
        addNavigationElements(array, pyClass);
      }
      for (PyFunction pyFunction : ((PyFile)psiElement).getTopLevelFunctions()) {
        addNavigationElements(array, pyFunction);
      }
    }
    else if (psiElement instanceof PyFunction){
      array.add(psiElement);
    }
    else if (psiElement instanceof PyClass) {
      Collections.addAll(array, ((PyClass)psiElement).getMethods());
    }
  }
}
