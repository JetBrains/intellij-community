// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.parsing.PropertiesElementTypes;
import com.intellij.lang.properties.psi.PropertiesList;
import com.intellij.lang.properties.psi.PropertiesListStub;
import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PropertiesListImpl extends PropertiesStubElementImpl<PropertiesListStub> implements PropertiesList {
  public PropertiesListImpl(final ASTNode node) {
    super(node);
  }

  public PropertiesListImpl(final PropertiesListStub stub) {
    super(stub, PropertiesElementTypes.PROPERTIES_LIST);
  }

  @Override
  public String toString() {
    return "PropertiesList";
  }

  @Override
  public @NotNull String getDocCommentText() {
    final Property firstProp = PsiTreeUtil.getChildOfType(this, Property.class);

    // If there are no properties in the property file,
    // then the whole content of the file is considered to be a doc comment
    if (firstProp == null) return getText();

    final PsiElement upperEdge = PropertyImpl.getEdgeOfProperty(firstProp);

    final List<PsiElement> comments = PsiTreeUtil.getChildrenOfTypeAsList(this, PsiElement.class);

    return comments.stream()
      .takeWhile(Predicate.not(upperEdge::equals))
      .map(PsiElement::getText)
      .collect(Collectors.joining());
  }
}
