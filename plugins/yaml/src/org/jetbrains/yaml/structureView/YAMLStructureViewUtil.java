// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.structureView;

import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.psi.YAMLAlias;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.YAMLValue;

import javax.swing.*;

import static org.jetbrains.yaml.structureView.YAMLStructureViewFactory.ALIAS_ICON;

final class YAMLStructureViewUtil {
  private YAMLStructureViewUtil() {}

  @Nullable
  static String getKeyValueLocationString(@NotNull YAMLKeyValue kv) {
    YAMLValue value = kv.getValue();
    if (value instanceof YAMLScalar) {
      return kv.getValueText();
    }
    else if (value instanceof YAMLAlias) {
      return YAMLStructureViewFactory.getAliasPresentableText((YAMLAlias)value);
    }
    else {
      return null;
    }
  }

  @Nullable
  static Icon getKeyValueIcon(@NotNull YAMLKeyValue kv) {
    YAMLValue value = kv.getValue();
    if (value instanceof YAMLScalar) {
      return kv.getIcon(0);
    }
    else if (value instanceof YAMLAlias) {
      return ALIAS_ICON;
    }
    else {
      return PlatformIcons.XML_TAG_ICON;
    }
  }

  @NotNull
  static String getSeqItemPresentableText(@Nullable YAMLValue itemValue) {
    if (itemValue instanceof YAMLScalar) {
      return ((YAMLScalar)itemValue).getTextValue();
    }
    else if (itemValue instanceof YAMLAlias) {
      return YAMLStructureViewFactory.getAliasPresentableText((YAMLAlias)itemValue);
    }
    else {
      return YAMLBundle.message("YAMLStructureViewSequenceItem.element.name");
    }
  }

  @NotNull
  static Icon getSeqItemIcon(@Nullable YAMLValue itemValue) {
    if (itemValue instanceof YAMLScalar) {
      return PlatformIcons.PROPERTY_ICON;
    }
    else if (itemValue instanceof YAMLAlias) {
      return ALIAS_ICON;
    }
    else {
      return PlatformIcons.XML_TAG_ICON;
    }
  }
}
