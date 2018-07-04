// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  public Collection<StructureViewTreeElement> getChildrenBase() {
    YAMLDocument document = Objects.requireNonNull(getElement());
    return YAMLStructureViewFactory.createChildrenViewTreeElements(document.getTopLevelValue(), "<doc>");
  }

  @NotNull
  public String getPresentableText() {
    return YAMLBundle.message("YAMLStructureViewDocument.element.name");
  }

  @NotNull
  public Icon getIcon(boolean open) {
    return PlatformIcons.XML_TAG_ICON;
  }
}
