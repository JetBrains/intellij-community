/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/**
 * @author Sergey Evdokimov
 */
public class MavenContextlessPropertyReference extends PsiReferenceBase<PsiElement> {

  private final XmlTag myProperties;

  public MavenContextlessPropertyReference(@NotNull XmlTag properties,
                                           @NotNull PsiElement element,
                                           @NotNull TextRange range,
                                           boolean isSoft) {
    super(element, range, isSoft);

    myProperties = properties;
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    String value = getValue();

    for (XmlTag subTag : myProperties.getSubTags()) {
      if (value.equals(subTag.getName())) {
        return subTag;
      }
    }

    return null;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
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
