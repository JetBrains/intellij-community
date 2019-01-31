// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  public Collection<StructureViewTreeElement> getChildrenBase() {
    return YAMLStructureViewFactory.createChildrenViewTreeElements(getItemValue(), null);
  }

  @Nullable
  @Override
  public String getPresentableText() {
    return YAMLStructureViewUtil.getSeqItemPresentableText(getItemValue());
  }

  @Nullable
  @Override
  public Icon getIcon(boolean unused) {
    return YAMLStructureViewUtil.getSeqItemIcon(getItemValue());
  }

  @Nullable
  private YAMLValue getItemValue() {
    return Objects.requireNonNull(getElement()).getValue();
  }
}
