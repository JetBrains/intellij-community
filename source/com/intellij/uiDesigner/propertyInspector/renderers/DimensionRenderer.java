package com.intellij.uiDesigner.propertyInspector.renderers;

import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class DimensionRenderer extends LabelPropertyRenderer {
  protected void customize(final Object value) {
    final Dimension dimension = (Dimension)value;
    setText("[" + dimension.width + ", " + dimension.height + "]");
  }
}
