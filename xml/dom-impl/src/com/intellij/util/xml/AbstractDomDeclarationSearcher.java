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
package com.intellij.util.xml;

import com.intellij.pom.PomDeclarationSearcher;
import com.intellij.pom.PomTarget;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
public abstract class AbstractDomDeclarationSearcher extends PomDeclarationSearcher {

  @Override
  public void findDeclarationsAt(@NotNull PsiElement psiElement, int offsetInElement, Consumer<PomTarget> consumer) {
    if (!(psiElement instanceof XmlToken)) return;

    final IElementType tokenType = ((XmlToken)psiElement).getTokenType();

    final DomManager domManager = DomManager.getDomManager(psiElement.getProject());
    final DomElement nameElement;
    if (tokenType == XmlTokenType.XML_DATA_CHARACTERS && psiElement.getParent() instanceof XmlText && psiElement.getParent().getParent() instanceof XmlTag) {
      final XmlTag tag = (XmlTag)psiElement.getParent().getParent();
      for (XmlText text : tag.getValue().getTextElements()) {
        if (InjectedLanguageUtil.hasInjections((PsiLanguageInjectionHost)text)) {
          return;
        }
      }

      nameElement = domManager.getDomElement(tag);
    } else if (tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN && psiElement.getParent() instanceof XmlAttributeValue && psiElement.getParent().getParent() instanceof XmlAttribute) {
      final PsiElement attributeValue = psiElement.getParent();
      if (InjectedLanguageUtil.hasInjections((PsiLanguageInjectionHost)attributeValue)) {
        return;
      }
      nameElement = domManager.getDomElement((XmlAttribute)attributeValue.getParent());
    } else {
      return;
    }

    if (!(nameElement instanceof GenericDomValue)) {
      return;
    }

    DomElement parent = nameElement.getParent();
    if (parent == null) {
      return;
    }

    final DomTarget target = createDomTarget(parent, nameElement);
    if (target != null) {
      consumer.consume(target);
    }
  }

  @Nullable
  protected abstract DomTarget createDomTarget(DomElement parent, DomElement nameElement);
}
