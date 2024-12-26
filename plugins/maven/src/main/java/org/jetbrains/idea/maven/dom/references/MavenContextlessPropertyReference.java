// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.references;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MavenContextlessPropertyReference extends PsiReferenceBase<PsiElement> {

  private final XmlTag myProperties;

  public MavenContextlessPropertyReference(@NotNull XmlTag properties,
                                           @NotNull PsiElement element,
                                           @NotNull TextRange range,
                                           boolean isSoft) {
    super(element, range, isSoft);

    myProperties = properties;
  }

  @Override
  public @Nullable PsiElement resolve() {
    String value = getValue();

    for (XmlTag subTag : myProperties.getSubTags()) {
      if (value.equals(subTag.getName())) {
        return subTag;
      }
    }

    return null;
  }

  @Override
  public Object @NotNull [] getVariants() {
    String skippedPropertyName = null;

    XmlTag xmlTag = PsiTreeUtil.getParentOfType(getElement(), XmlTag.class, false);

    if (xmlTag != null && myProperties == xmlTag.getParentTag()) {
      skippedPropertyName = xmlTag.getName();
    }

    List<String> res = new ArrayList<>();

    for (XmlTag subTag : myProperties.getSubTags()) {
      String name = subTag.getName();
      if (!name.equals(skippedPropertyName)) {
        res.add(name);
      }
    }

    return res.toArray();
  }
}
