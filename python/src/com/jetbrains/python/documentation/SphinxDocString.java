package com.jetbrains.python.documentation;

import java.util.List;

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

  @Override
  public String getReturnDescription() {
    return getTagValue("return");
  }

  @Override
  public List<String> getRaisedExceptions() {
    return getTagArguments(EpydocString.RAISES_TAGS);
  }

  @Override
  public String getRaisedExceptionDescription(String exceptionName) {
    return getTagValue(EpydocString.RAISES_TAGS, exceptionName);
  }
}
