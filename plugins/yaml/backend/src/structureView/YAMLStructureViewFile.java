// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @Override
  public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
    YAMLFile file = Objects.requireNonNull(getElement());
    return ContainerUtil.map(file.getDocuments(), YAMLStructureViewDocument::new);
  }

  @Override
  public @Nullable String getPresentableText() {
    return getFilePresentation().getPresentableText();
  }

  @Override
  public @Nullable String getLocationString() {
    return getFilePresentation().getLocationString();
  }

  @Override
  public @Nullable Icon getIcon(boolean unused) {
    return getFilePresentation().getIcon(unused);
  }

  private @NotNull ItemPresentation getFilePresentation() {
    YAMLFile file = Objects.requireNonNull(getElement());
    return Objects.requireNonNull(file.getPresentation());
  }
}
