package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.uiDesigner.lw.StringDescriptor;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class StringRenderer extends LabelPropertyRenderer<StringDescriptor> {

  protected void customize(final StringDescriptor value) {
    if (value != null) {
      setText(value.getResolvedValue());
    }
    else{
      setText(null);
    }
  }
}
