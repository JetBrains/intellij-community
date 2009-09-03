package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class StringRenderer extends LabelPropertyRenderer<StringDescriptor> {

  protected void customize(@NotNull final StringDescriptor value) {
    String resolvedValue = value.getResolvedValue();
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
