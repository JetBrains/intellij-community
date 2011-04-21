/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.theoryinpractice.testng;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.proximity.ForcedElementWeigher;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: 4/19/11
 */
public class TestNGForcedElementWeigher implements ForcedElementWeigher {
  @Nullable
  @Override
  public Comparable getForcedWeigh(PsiElement element) {
    if (element instanceof PsiClass) {
      final String qualifiedName = ((PsiClass)element).getQualifiedName();
      if (qualifiedName != null && qualifiedName.startsWith("org.testng.internal")) {
        return -1;
      }
    }
    return null;
  }
}
