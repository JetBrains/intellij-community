/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 14-May-2007
 */
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInspection.reference.EntryPoint;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestNGEntryPoint extends EntryPoint {
   public boolean ADD_TESTNG_TO_ENTRIES = true;

  public boolean isSelected() {
    return ADD_TESTNG_TO_ENTRIES;
  }

  public void setSelected(boolean selected) {
    ADD_TESTNG_TO_ENTRIES = selected;
  }

  @NotNull
  public String getDisplayName() {
    return "TestNG test cases";
  }

  public boolean isEntryPoint(@NotNull RefElement refElement, @NotNull PsiElement psiElement) {
    return isEntryPoint(psiElement);
  }

  @Override
  public boolean isEntryPoint(@NotNull PsiElement psiElement) {
    if (ADD_TESTNG_TO_ENTRIES) {
      if (psiElement instanceof PsiModifierListOwner) {
        if (TestNGUtil.hasTest((PsiModifierListOwner)psiElement, false, false, TestNGUtil.hasDocTagsSupport)) return true;
        return TestNGUtil.hasConfig((PsiModifierListOwner)psiElement);
      }
    }
    return false;
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    if (!ADD_TESTNG_TO_ENTRIES) {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }
  }

  @Nullable
  public String[] getIgnoreAnnotations() {
    return TestNGUtil.CONFIG_ANNOTATIONS_FQN;
  }
}