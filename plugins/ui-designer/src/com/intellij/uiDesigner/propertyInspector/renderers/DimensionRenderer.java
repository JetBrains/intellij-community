// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.renderers;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

public final class DimensionRenderer extends LabelPropertyRenderer<Dimension> {
  @Override
  protected void customize(@NotNull final Dimension value) {
    setText("[" + value.width + ", " + value.height + "]");
  }
}
