/*
 * @author max
 */
package com.intellij.lang.properties;

import com.intellij.lang.DefaultWordCompletionFilter;
import com.intellij.lang.properties.parsing.PropertiesElementTypes;
import com.intellij.psi.tree.IElementType;

public class PropertiesWordCompletionFilter extends DefaultWordCompletionFilter {
  public boolean isWordCompletionEnabledIn(final IElementType element) {
    return super.isWordCompletionEnabledIn(element) || element == PropertiesElementTypes.PROPERTY;
  }
}