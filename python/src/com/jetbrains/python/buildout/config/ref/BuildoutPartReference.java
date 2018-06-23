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
package com.jetbrains.python.buildout.config.ref;

import com.google.common.collect.Lists;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PythonStringUtil;
import com.jetbrains.python.buildout.config.psi.impl.BuildoutCfgFile;
import com.jetbrains.python.buildout.config.psi.impl.BuildoutCfgSection;
import com.jetbrains.python.psi.PyElementGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author traff
 */
public class BuildoutPartReference extends PsiReferenceBase<PsiElement> {
  private final String myPartName;
  private final int myOffsetInElement;

  public BuildoutPartReference(PsiElement element, String partName, int offsetInElement) {
    super(element);
    myPartName = partName;
    myOffsetInElement = offsetInElement;
  }

  @NotNull
  @Override
  public TextRange getRangeInElement() {
    return TextRange.from(myOffsetInElement, myPartName.length());
  }

  public PsiElement resolve() {
    BuildoutCfgFile file = PsiTreeUtil.getParentOfType(myElement, BuildoutCfgFile.class);
    if (file != null) {
      return file.findSectionByName(myPartName);
    }
    return null;
  }

  @NotNull
  public Object[] getVariants() {
    List<String> res = Lists.newArrayList();
    BuildoutCfgFile file = PsiTreeUtil.getParentOfType(myElement, BuildoutCfgFile.class);
    if (file != null) {
      for (BuildoutCfgSection sec : file.getSections()) {
        String name = sec.getHeaderName();
        if (name != null) {
          res.add(name);
        }
      }
      return res.toArray();
    }
    return EMPTY_ARRAY;
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) {
    String fullName = PythonStringUtil.replaceLastSuffix(getElement().getText(), "/", newElementName);
    return myElement.replace(PyElementGenerator.getInstance(myElement.getProject()).createStringLiteralAlreadyEscaped(fullName));
  }
}
