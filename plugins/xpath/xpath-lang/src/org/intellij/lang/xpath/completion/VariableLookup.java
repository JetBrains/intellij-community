/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.completion;

import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

class VariableLookup extends AbstractLookup implements ElementProvider {
  private final String myType;
  private final Icon myIcon;
  private final PsiElement myPsiElement;

  VariableLookup(String name, Icon icon) {
    this(name, "", icon, null);
  }

  VariableLookup(String name, String type, Icon icon, PsiElement psiElement) {
    super(name, name);
    myType = type;
    myIcon = icon;
    myPsiElement = psiElement;
  }

  @Override
  public void renderElement(@NotNull LookupElementPresentation presentation) {
    super.renderElement(presentation);
    presentation.setTypeText(myType);
    presentation.setIcon(myIcon != null ? myIcon : IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Variable));
  }

  @Override
  public PsiElement getElement() {
    return myPsiElement;
  }
}
