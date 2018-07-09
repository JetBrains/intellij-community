// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.navigation.ItemPresentation;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLFile;

import javax.swing.*;
import java.util.Collection;
import java.util.Objects;

class YAMLStructureViewFile extends PsiTreeElementBase<YAMLFile> {
  YAMLStructureViewFile(@NotNull YAMLFile psiElement) {
    super(psiElement);
  }

  @NotNull
  @Override
  public Collection<StructureViewTreeElement> getChildrenBase() {
    YAMLFile file = Objects.requireNonNull(getElement());
    return ContainerUtil.map(file.getDocuments(), YAMLStructureViewDocument::new);
  }

  @Nullable
  @Override
  public String getPresentableText() {
    return getFilePresentation().getPresentableText();
  }

  @Nullable
  public String getLocationString() {
    return getFilePresentation().getLocationString();
  }

  @Nullable
  public Icon getIcon(boolean unused) {
    return getFilePresentation().getIcon(unused);
  }

  @NotNull
  private ItemPresentation getFilePresentation() {
    YAMLFile file = Objects.requireNonNull(getElement());
    return Objects.requireNonNull(file.getPresentation());
  }
}
