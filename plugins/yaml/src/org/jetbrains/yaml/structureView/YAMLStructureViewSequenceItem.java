// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.psi.YAMLAlias;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import org.jetbrains.yaml.psi.YAMLValue;

import javax.swing.*;
import java.util.Collection;

import static org.jetbrains.yaml.structureView.YAMLStructureViewFactory.ALIAS_ICON;

class YAMLStructureViewSequenceItem extends DuplicatedPsiTreeElementBase<YAMLSequenceItem> {
  YAMLStructureViewSequenceItem(@NotNull YAMLSequenceItem item, String path) {
    super(item, path + '-' + item.getItemIndex());
  }

  @NotNull
  @Override
  public Collection<StructureViewTreeElement> getChildrenBase() {
    return YAMLStructureViewFactory.createChildrenViewTreeElements(getItemValue(), getDetails());
  }

  @NotNull
  @Override
  public String getPresentableText() {
    if (getItemValue() instanceof YAMLScalar) {
      return ((YAMLScalar)getItemValue()).getTextValue();
    }
    else if (getItemValue() instanceof YAMLAlias) {
      return YAMLStructureViewFactory.getAliasPresentableText((YAMLAlias)getItemValue());
    }
    else {
      return YAMLBundle.message("YAMLStructureViewSequenceItem.element.name");
    }
  }

  @Nullable
  @Override
  public String getLocationString() {
    return null;
  }

  @NotNull
  @Override
  public Icon getIcon(boolean unused) {
    if (getItemValue() instanceof YAMLScalar) {
      return PlatformIcons.PROPERTY_ICON;
    }
    else if (getItemValue() instanceof YAMLAlias) {
      return ALIAS_ICON;
    }
    else {
      return PlatformIcons.XML_TAG_ICON;
    }
  }

  @Nullable
  private YAMLValue getItemValue() {
    return getElement().getValue();
  }
}
