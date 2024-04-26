// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.psi.YAMLDocument;

import javax.swing.*;
import java.util.Collection;
import java.util.Objects;

class YAMLStructureViewDocument extends PsiTreeElementBase<YAMLDocument> {
  YAMLStructureViewDocument(@NotNull YAMLDocument psiElement) {
    super(psiElement);
  }

  @Override
  public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
    YAMLDocument document = Objects.requireNonNull(getElement());
    return YAMLStructureViewFactory.createChildrenViewTreeElements(document.getTopLevelValue(), null);
  }

  @Override
  public @NotNull String getPresentableText() {
    return YAMLBundle.message("YAMLStructureViewDocument.element.name");
  }

  @Override
  public @NotNull Icon getIcon(boolean open) {
    return PlatformIcons.XML_TAG_ICON;
  }
}
