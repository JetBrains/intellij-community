package com.intellij.lang.properties.parsing;

import com.intellij.psi.tree.IElementType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 27, 2005
 * Time: 6:38:56 PM
 * To change this template use File | Settings | File Templates.
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
