package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.uiDesigner.lw.StringDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class StringRenderer extends LabelPropertyRenderer<StringDescriptor> {

  protected void customize(@NotNull final StringDescriptor value) {
    final String resolvedValue = value.getResolvedValue();
    if (resolvedValue != null) {
      setText(resolvedValue);
    }
    else {
      setText(value.getValue());
    }
  }
}
