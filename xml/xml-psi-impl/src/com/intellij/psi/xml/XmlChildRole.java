// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.xml;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.DefaultRoleFinder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.RoleFinder;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface XmlChildRole {
  RoleFinder START_TAG_NAME_FINDER = new RoleFinder() {
    @Override
    public ASTNode findChild(@NotNull ASTNode parent) {
      final PsiElement element = XmlTagUtil.getStartTagNameElement((XmlTag)parent.getPsi());
      return element == null ? null : element.getNode();
    }
  };

  RoleFinder CLOSING_TAG_NAME_FINDER = new RoleFinder() {
    @Override
    public @Nullable ASTNode findChild(@NotNull ASTNode parent) {
      final PsiElement element = XmlTagUtil.getEndTagNameElement((XmlTag)parent.getPsi());
      return element == null ? null : element.getNode();
    }
  };

  RoleFinder ATTRIBUTE_VALUE_FINDER = new DefaultRoleFinder(XmlElementType.XML_ATTRIBUTE_VALUE);
  RoleFinder CLOSING_TAG_START_FINDER = new DefaultRoleFinder(XmlTokenType.XML_END_TAG_START);
  RoleFinder EMPTY_TAG_END_FINDER = new DefaultRoleFinder(XmlTokenType.XML_EMPTY_ELEMENT_END);
  RoleFinder ATTRIBUTE_NAME_FINDER = new DefaultRoleFinder(XmlTokenType.XML_NAME);
  RoleFinder ATTRIBUTE_VALUE_VALUE_FINDER = new DefaultRoleFinder(XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN);


  RoleFinder START_TAG_END_FINDER = new DefaultRoleFinder(() -> {
    return StartTagEndTokenProvider.EP_NAME.computeIfAbsent(XmlChildRole.class, () -> {
      IElementType[] elementTypes = new IElementType[]{XmlTokenType.XML_TAG_END};
      for (StartTagEndTokenProvider tokenProvider : StartTagEndTokenProvider.EP_NAME.getExtensionList()) {
        elementTypes = ArrayUtil.mergeArrays(elementTypes, tokenProvider.getTypes());
      }
      return elementTypes;
    });
  });

  RoleFinder START_TAG_START_FINDER = new DefaultRoleFinder(XmlTokenType.XML_START_TAG_START);
}