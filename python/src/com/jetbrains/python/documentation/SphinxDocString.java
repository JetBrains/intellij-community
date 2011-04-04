package com.jetbrains.python.documentation;

/**
 * @author yole
 */
public class SphinxDocString extends StructuredDocString {
  public SphinxDocString(String docstringText) {
    super(docstringText, ":");
  }

  @Override
  public String getReturnType() {
    return getTagValue("rtype");
  }

  @Override
  public String getParamType(String paramName) {
    return getTagValue("type", paramName);
  }

  @Override
  public String getParamDescription(String paramName) {
    return getTagValue("param", paramName);
  }
}
