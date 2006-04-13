package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.psi.PsiKeyword;

import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class DimensionRenderer extends LabelPropertyRenderer<Dimension> {
  protected void customize(final Dimension value) {
    if (value == null) {
      setText("[" + PsiKeyword.NULL + "]");
    }
    else {
      setText("[" + value.width + ", " + value.height + "]");
    }
  }
}
