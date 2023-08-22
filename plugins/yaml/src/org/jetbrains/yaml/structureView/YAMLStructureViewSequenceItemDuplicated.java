// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  public Collection<StructureViewTreeElement> getChildrenBase() {
    return YAMLStructureViewFactory.createChildrenViewTreeElements(getItemValue(), getDetails());
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
    return getElement().getValue();
  }
}
