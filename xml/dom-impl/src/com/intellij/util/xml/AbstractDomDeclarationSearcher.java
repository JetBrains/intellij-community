// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  public void findDeclarationsAt(@NotNull PsiElement token, int offsetInElement, @NotNull Consumer<PomTarget> consumer) {
    if (!(token instanceof XmlToken)) return;
    final PsiElement element = token.getParent();
    if (element == null) return;
    final IElementType tokenType = ((XmlToken)token).getTokenType();
    final PsiElement parentElement = element.getParent();
    final DomManager domManager = DomManager.getDomManager(token.getProject());
    final DomElement nameElement;
    if (tokenType == XmlTokenType.XML_DATA_CHARACTERS && element instanceof XmlText && parentElement instanceof XmlTag) {
      final XmlTag tag = (XmlTag)parentElement;
      for (XmlText text : tag.getValue().getTextElements()) {
        if (InjectedLanguageUtil.hasInjections((PsiLanguageInjectionHost)text)) {
          return;
        }
      }

      nameElement = domManager.getDomElement(tag);
    }
    else if (tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN &&
             element instanceof XmlAttributeValue &&
             parentElement instanceof XmlAttribute) {
      if (InjectedLanguageUtil.hasInjections((PsiLanguageInjectionHost)element)) {
        return;
      }
      nameElement = domManager.getDomElement((XmlAttribute)parentElement);
    }
    else {
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
