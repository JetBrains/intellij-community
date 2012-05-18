package com.intellij.structuralsearch.plugin.ui;

import com.intellij.openapi.application.PathMacroFilter;
import org.jdom.Attribute;

/**
 * @author yole
 */
public class StructuralSearchPathMacroFilter extends PathMacroFilter {
  @Override
  public boolean skipPathMacros(Attribute attribute) {
    final String parentName = attribute.getParent().getName();
    if (ConfigurationManager.REPLACE_TAG_NAME.equals(parentName) || ConfigurationManager.SEARCH_TAG_NAME.equals(parentName)) {
      return true;
    }
    return false;
  }
}
