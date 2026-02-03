// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.structureView;

import com.intellij.ide.structureView.StructureViewBundle;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.LocationPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.PsiFileImpl;
import org.intellij.plugins.markdown.util.MarkdownPsiStructureUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

 class MarkdownStructureElement extends PsiTreeElementBase<PsiElement> implements SortableTreeElement, LocationPresentation,
                                                                                  Queryable {

  private static final ItemPresentation DUMMY_PRESENTATION = new MarkdownBasePresentation() {
    @Override
    public @Nullable String getPresentableText() {
      return null;
    }
  };

  MarkdownStructureElement(@NotNull PsiElement element) {
    super(element);
  }

  @Override
  public boolean canNavigate() {
    return getElement() instanceof NavigationItem && ((NavigationItem)getElement()).canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return getElement() instanceof NavigationItem && ((NavigationItem)getElement()).canNavigateToSource();
  }


  @Override
  public void navigate(boolean requestFocus) {
    if (getElement() instanceof NavigationItem) {
      ((NavigationItem)getElement()).navigate(requestFocus);
    }
  }

  @Override
  public @NotNull String getAlphaSortKey() {
    return StringUtil.notNullize(getElement() instanceof NavigationItem ?
                                 ((NavigationItem)getElement()).getName() : null);
  }

  @Override
  public boolean isSearchInLocationString() {
    return true;
  }

  @Override
  public @Nullable String getPresentableText() {
    final PsiElement tag = getElement();
    if (tag == null) {
      return StructureViewBundle.message("node.structureview.invalid");
    }
    return getPresentation().getPresentableText();
  }

  @Override
  public String getLocationString() {
    return getPresentation().getLocationString();
  }

  @Override
  public @NotNull ItemPresentation getPresentation() {
    if (getElement() instanceof PsiFileImpl) {
      ItemPresentation filePresent = ((PsiFileImpl)getElement()).getPresentation();
      return filePresent != null ? filePresent : DUMMY_PRESENTATION;
    }

    if (getElement() instanceof NavigationItem) {
      final ItemPresentation itemPresent = ((NavigationItem)getElement()).getPresentation();
      if (itemPresent != null) {
        return itemPresent;
      }
    }

    return DUMMY_PRESENTATION;
  }


  @Override
  public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
    final ArrayList<StructureViewTreeElement> elements = new ArrayList<>();
    MarkdownPsiStructureUtil.processContainer(getElement(), element -> elements.add(new MarkdownStructureElement(element)));
    return elements;
  }

  @Override
  public @NotNull String getLocationPrefix() {
    return " ";
  }

  @Override
  public @NotNull String getLocationSuffix() {
    return "";
  }

  @Override
  public void putInfo(@NotNull Map<? super String, ? super String> info) {
    info.put("text", getPresentableText());
    if (!(getElement() instanceof PsiFileImpl)) {
      info.put("location", getLocationString());
    }
  }
}
