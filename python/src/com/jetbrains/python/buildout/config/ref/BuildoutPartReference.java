// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
  public PsiElement resolve() {
    BuildoutCfgFile file = PsiTreeUtil.getParentOfType(myElement, BuildoutCfgFile.class);
    if (file != null) {
      return file.findSectionByName(myPartName);
    }
    return null;
  }

  @Override
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
