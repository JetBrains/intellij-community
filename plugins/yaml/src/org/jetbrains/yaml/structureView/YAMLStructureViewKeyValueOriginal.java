// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import javax.swing.*;
import java.util.Collection;
import java.util.Objects;

/**
 * Original class is needed to fix Autoscroll from Source functionality.
 * Autoscroll works correctly if structure view element has {@link PsiElement} as a return type of
 * {@link StructureViewTreeElement#getValue()}
 */
class YAMLStructureViewKeyValueOriginal extends PsiTreeElementBase<YAMLKeyValue> {
  YAMLStructureViewKeyValueOriginal(@NotNull YAMLKeyValue kv) {
    super(kv);
  }

  @NotNull
  @Override
  public Collection<StructureViewTreeElement> getChildrenBase() {
    return YAMLStructureViewFactory.createChildrenViewTreeElements(getKeyValue().getValue(), null);
  }

  @Override
  public String getLocationString() {
    return YAMLStructureViewUtil.getKeyValueLocationString(getKeyValue());
  }

  @Nullable
  @Override
  public String getPresentableText() {
    return getKeyValue().getKeyText();
  }

  @Override
  public Icon getIcon(boolean open) {
    return YAMLStructureViewUtil.getKeyValueIcon(getKeyValue());
  }

  @NotNull
  private YAMLKeyValue getKeyValue() {
    return Objects.requireNonNull(getElement());
  }
}
