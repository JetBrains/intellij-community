package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.uiDesigner.lw.StringDescriptor;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class StringRenderer extends LabelPropertyRenderer{
  public StringRenderer() {
  }

  protected void customize(final Object value) {
    if (value != null) {
      final StringDescriptor descriptor = (StringDescriptor)value;
      setText(descriptor.getResolvedValue());
    }
    else{
      setText(null);
    }
  }
}
