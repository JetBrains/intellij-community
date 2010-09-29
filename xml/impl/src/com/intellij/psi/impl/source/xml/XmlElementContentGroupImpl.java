/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.impl.source.PsiElementArrayConstructor;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlElementContentGroup;
import com.intellij.psi.xml.XmlElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class XmlElementContentGroupImpl  extends XmlElementImpl implements XmlElementContentGroup, XmlElementType {

  private static final TokenSet FILTER = TokenSet.create(XML_ELEMENT_CONTENT_GROUP);
  private static final PsiElementArrayConstructor<XmlElementContentGroup> ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<XmlElementContentGroup>() {
    @Override
    public XmlElementContentGroup[] newPsiElementArray(int length) {
      return new XmlElementContentGroup[length];
    }
  };

  private NotNullLazyValue<XmlElementContentGroup[]> mySubGroups = new NotNullLazyValue<XmlElementContentGroup[]>() {
    @NotNull
    @Override
    protected XmlElementContentGroup[] compute() {
      return getChildrenAsPsiElements(FILTER, ARRAY_CONSTRUCTOR);
    }
  };

  public XmlElementContentGroupImpl() {
    super(XML_ELEMENT_CONTENT_GROUP);
  }

  @Override
  public XmlElementContentGroup[] getSubGroups() {
    return mySubGroups.getValue();
  }
}
