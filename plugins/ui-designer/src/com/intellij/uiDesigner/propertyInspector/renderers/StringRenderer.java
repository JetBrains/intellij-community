// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.uiDesigner.lw.StringDescriptor;
import org.jetbrains.annotations.NotNull;

public final class StringRenderer extends LabelPropertyRenderer<StringDescriptor> {

  @Override
  protected void customize(@NotNull final StringDescriptor value) {
    @NlsSafe String resolvedValue = value.getResolvedValue();
    if (resolvedValue == null) {
      resolvedValue = value.getValue();
    }
    if (resolvedValue != null) {
      setText(StringUtil.escapeStringCharacters(resolvedValue));
    }
    else {
      setText("");
    }
  }
}
