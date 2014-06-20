package com.intellij.structuralsearch.impl.matcher;

/**
* @author Eugene.Kudelevsky
*/
public class XmlCompiledPattern extends CompiledPattern {
  private static final String XML_TYPED_VAR_PREFIX = "__";

  public String[] getTypedVarPrefixes() {
    return new String[] {XML_TYPED_VAR_PREFIX};
  }

  public boolean isTypedVar(final String str) {
    return str.startsWith(XML_TYPED_VAR_PREFIX);
  }
}
