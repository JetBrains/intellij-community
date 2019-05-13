package org.intellij.lang.xpath.context.functions;

import org.intellij.lang.xpath.psi.XPathType;
import org.jetbrains.annotations.NotNull;

public interface Function {
  String getName();

  @NotNull
  Parameter[] getParameters();

  @NotNull
  XPathType getReturnType();

  int getMinArity();

  String buildSignature();
}