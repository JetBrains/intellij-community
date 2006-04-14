package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.psi.PsiKeyword;

import java.awt.*;

import org.jetbrains.annotations.NotNull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class DimensionRenderer extends LabelPropertyRenderer<Dimension> {
  protected void customize(@NotNull final Dimension value) {
    setText("[" + value.width + ", " + value.height + "]");
  }
}
