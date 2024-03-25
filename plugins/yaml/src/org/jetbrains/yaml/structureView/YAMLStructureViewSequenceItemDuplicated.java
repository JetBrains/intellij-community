// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import org.jetbrains.yaml.psi.YAMLValue;

import javax.swing.*;
import java.util.Collection;

class YAMLStructureViewSequenceItemDuplicated extends DuplicatedPsiTreeElementBase<YAMLSequenceItem> {
  YAMLStructureViewSequenceItemDuplicated(@NotNull YAMLSequenceItem item, String path) {
    super(item, path + '-' + item.getItemIndex());
  }

  @Override
  public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
    return YAMLStructureViewFactory.createChildrenViewTreeElements(getItemValue(), getDetails());
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
    return getElement().getValue();
  }
}
