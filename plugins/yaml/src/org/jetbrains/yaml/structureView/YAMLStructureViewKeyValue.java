// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLAlias;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.YAMLValue;

import javax.swing.*;
import java.util.Collection;
import java.util.Objects;

import static org.jetbrains.yaml.structureView.YAMLStructureViewFactory.ALIAS_ICON;

class YAMLStructureViewKeyValue extends DuplicatedPsiTreeElementBase<YAMLKeyValue> {
  YAMLStructureViewKeyValue(@NotNull YAMLKeyValue kv, String path) {
    super(kv, path + '.' + kv.getKeyText());
  }

  @NotNull
  @Override
  public Collection<StructureViewTreeElement> getChildrenBase() {
    return YAMLStructureViewFactory.createChildrenViewTreeElements(getVal(), getDetails());
  }

  @NotNull
  public String getPresentableText() {
    return getKeyValue().getKeyText();
  }

  @Nullable
  public String getLocationString() {
    if (getVal() instanceof YAMLScalar) {
      return getKeyValue().getValueText();
    }
    else if (getVal() instanceof YAMLAlias) {
      return YAMLStructureViewFactory.getAliasPresentableText((YAMLAlias)getVal());
    }
    else {
      return null;
    }
  }

  @Nullable
  public Icon getIcon(boolean open) {
    if (getVal() instanceof YAMLScalar) {
      return getKeyValue().getIcon(0);
    }
    else if (getVal() instanceof YAMLAlias) {
      return ALIAS_ICON;
    }
    else {
      return PlatformIcons.XML_TAG_ICON;
    }
  }

  @NotNull
  private YAMLKeyValue getKeyValue() {
    return Objects.requireNonNull(getElement());
  }

  // Base class already has getValue() method with different semantic
  @Nullable
  private YAMLValue getVal() {
    return getKeyValue().getValue();
  }
}
