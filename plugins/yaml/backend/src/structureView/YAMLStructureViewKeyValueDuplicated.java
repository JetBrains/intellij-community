// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import javax.swing.*;
import java.util.Collection;
import java.util.Objects;

class YAMLStructureViewKeyValueDuplicated extends DuplicatedPsiTreeElementBase<YAMLKeyValue> {
  YAMLStructureViewKeyValueDuplicated(@NotNull YAMLKeyValue kv, String path) {
    super(kv, path + '.' + kv.getKeyText());
  }

  @Override
  public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
    return YAMLStructureViewFactory.createChildrenViewTreeElements(getKeyValue().getValue(), getDetails());
  }

  @Override
  public String getLocationString() {
    return YAMLStructureViewUtil.getKeyValueLocationString(getKeyValue());
  }

  @Override
  public @Nullable String getPresentableText() {
    return getKeyValue().getKeyText();
  }

  @Override
  public Icon getIcon(boolean open) {
    return YAMLStructureViewUtil.getKeyValueIcon(getKeyValue());
  }

  private @NotNull YAMLKeyValue getKeyValue() {
    return Objects.requireNonNull(getElement());
  }
}
