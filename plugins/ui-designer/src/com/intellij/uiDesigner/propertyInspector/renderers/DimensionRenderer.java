// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector.renderers;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

public final class DimensionRenderer extends LabelPropertyRenderer<Dimension> {
  @Override
  protected void customize(final @NotNull Dimension value) {
    setText("[" + value.width + ", " + value.height + "]");
  }
}
