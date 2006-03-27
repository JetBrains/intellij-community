package com.intellij.uiDesigner.propertyInspector.renderers;

import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class DimensionRenderer extends LabelPropertyRenderer<Dimension> {
  protected void customize(final Dimension value) {
    setText("[" + value.width + ", " + value.height + "]");
  }
}
