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
package com.jetbrains.python.refactoring.changeSignature;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.UsageInfo;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * User : ktisha
 */

public class PyChangeSignatureUsageViewDescriptor extends UsageViewDescriptorAdapter {
  protected PsiElement[] myDeclarationsElements;

  public PyChangeSignatureUsageViewDescriptor(UsageInfo[] usages) {
    final Collection<PsiElement> declarationsElements = new ArrayList<>();
    for (UsageInfo info : usages) {
      declarationsElements.add(info.getElement());
    }
    myDeclarationsElements = PsiUtilCore.toPsiElementArray(declarationsElements);
  }

  @Override
  public PsiElement @NotNull [] getElements() {
    return myDeclarationsElements;
  }

  @Override
  public String getProcessedElementsHeader() {
    return PyBundle.message("refactoring.change.signature.usage.view.declarations.header");
  }
}
