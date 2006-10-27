package com.intellij.lang.properties.parsing;

import com.intellij.psi.tree.IElementType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import org.jetbrains.annotations.NonNls;

/**
 * @author max
 */
public class PropertiesElementType extends IElementType {
  public PropertiesElementType(@NonNls String debugName) {
    super(debugName, StdFileTypes.PROPERTIES.getLanguage());
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "Properties:" + super.toString();
  }
}
