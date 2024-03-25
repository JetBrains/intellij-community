// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import org.jetbrains.yaml.psi.YAMLValue;

import javax.swing.*;
import java.util.Collection;
import java.util.Objects;

/**
 * Original class is needed to fix Autoscroll from Source functionality.
 * Autoscroll works correctly if structure view element has {@link PsiElement} as a return type of
 * {@link StructureViewTreeElement#getValue()}
 */
class YAMLStructureViewSequenceItemOriginal extends PsiTreeElementBase<YAMLSequenceItem> {
  YAMLStructureViewSequenceItemOriginal(@NotNull YAMLSequenceItem item) {
    super(item);
  }

  @Override
  public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
    return YAMLStructureViewFactory.createChildrenViewTreeElements(getItemValue(), null);
  }

  @Override
  public @Nullable String getPresentableText() {
    return YAMLStructureViewUtil.getSeqItemPresentableText(getItemValue());
  }

  @Override
  public @Nullable Icon getIcon(boolean unused) {
    return YAMLStructureViewUtil.getSeqItemIcon(getItemValue());
  }

  private @Nullable YAMLValue getItemValue() {
    return Objects.requireNonNull(getElement()).getValue();
  }
}
