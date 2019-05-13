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
package com.intellij.psi.impl.smartPointers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dennis.Ushakov
 */
public class XmlAnchorProvider extends SmartPointerAnchorProvider {
  @Nullable
  @Override
  public PsiElement getAnchor(@NotNull PsiElement element) {
    if (element instanceof XmlTag) {
      return XmlTagUtil.getStartTagNameElement((XmlTag)element);
    }
    return null;
  }

  @Nullable
  @Override
  public PsiElement restoreElement(@NotNull PsiElement anchor) {
    if (anchor instanceof XmlToken) {
      XmlToken token = (XmlToken)anchor;
      return token.getTokenType() == XmlTokenType.XML_NAME ? token.getParent() : null;
    }
    return null;
  }
}
