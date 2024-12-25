// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.html.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

class Html5SectionTreeElement extends PsiTreeElementBase<XmlTag> {

  private final Computable<? extends Collection<StructureViewTreeElement>> myChildrenComputable;
  private final String myHeader;

  Html5SectionTreeElement(final XmlTag tag,
                                 final Computable<? extends Collection<StructureViewTreeElement>> childrenComputable,
                                 final @Nullable String header) {
    super(tag);
    myChildrenComputable = childrenComputable;
    myHeader = header;
  }

  @Override
  public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
    return myChildrenComputable.compute();
  }

  @Override
  public @Nullable String getPresentableText() {
    if (myHeader != null) {
      return HtmlTagTreeElement.normalizeSpacesAndShortenIfLong(myHeader);
    }

    final XmlTag tag = getElement();
    return tag == null ? null : HtmlTagTreeElement.normalizeSpacesAndShortenIfLong(tag.getValue().getTrimmedText());
  }

  @Override
  public String getLocationString() {
    return HtmlUtil.getTagPresentation(getElement());
  }

  @Override
  public boolean isSearchInLocationString() {
    return true;
  }
}
