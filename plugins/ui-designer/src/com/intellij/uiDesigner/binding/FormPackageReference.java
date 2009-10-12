/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.uiDesigner.binding;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.PsiPackage;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class FormPackageReference extends ReferenceInForm {
  protected FormPackageReference(final PsiPlainTextFile file, TextRange range) {
    super(file, range);
  }

  public PsiElement resolve() {
    final Project project = myFile.getProject();
    String text = getRangeText().replace('/', '.');
    return JavaPsiFacade.getInstance(project).findPackage(text);
  }

  @Override
  public boolean isReferenceTo(final PsiElement element) {
    if (!(element instanceof PsiPackage)) {
      return false;
    }
    final String qName = ((PsiPackage)element).getQualifiedName().replace('.', '/');
    final String rangeText = getRangeText();
    return qName.equals(rangeText);
  }

  @Override
  public PsiElement handleElementRename(final String newElementName) {
    final String s = getRangeText();
    int pos = s.lastIndexOf("/");
    if (pos < 0) {
      updateRangeText(newElementName);
    }
    else {
      updateRangeText(s.substring(0, pos+1) + newElementName);
    }
    return myFile;
  }

  public PsiElement bindToElement(@NotNull final PsiElement element) throws IncorrectOperationException {
    return myFile;
  }
}
