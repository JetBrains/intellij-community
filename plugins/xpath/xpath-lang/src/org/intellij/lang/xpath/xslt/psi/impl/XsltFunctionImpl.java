// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.xpath.xslt.psi.impl;

import com.intellij.icons.AllIcons;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.xpath.context.functions.Function;
import org.intellij.lang.xpath.context.functions.FunctionImpl;
import org.intellij.lang.xpath.context.functions.Parameter;
import org.intellij.lang.xpath.psi.XPath2Type;
import org.intellij.lang.xpath.psi.XPathElementVisitor;
import org.intellij.lang.xpath.psi.XPathType;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.XsltFunction;
import org.intellij.lang.xpath.xslt.util.QNameUtil;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.xml.namespace.QName;

final class XsltFunctionImpl extends XsltElementImpl implements XsltFunction, ItemPresentation {
  private static final NotNullFunction<XmlTag,Parameter> PARAM_MAPPER = param -> {
    final XPathType type = XsltCodeInsightUtil.getDeclaredType(param);
    return new Parameter(type != null ? type : XPath2Type.SEQUENCE, Parameter.Kind.REQUIRED);
  };

  XsltFunctionImpl(XmlTag target) {
    super(target);
  }

  private Function getFunction() {
    final XPathType returnType = XsltCodeInsightUtil.getDeclaredType(getTag());
    final XmlTag[] params = getTag().findSubTags("param", XsltSupport.XSLT_NS);
    final Parameter[] parameters = ContainerUtil.map2Array(params, Parameter.class, PARAM_MAPPER);

    return new FunctionImpl(null, returnType != null ? returnType : XPathType.UNKNOWN, parameters) {
      @Override
      public String getName() {
        return getQName().getLocalPart();
      }
    };
  }

  @Override
  public QName getQName() {
    final String name = getTag().getAttributeValue("name");
    assert name != null;
    return QNameUtil.createQName(name, getTag());
  }

  @Override
  public Icon getIcon(boolean open) {
    return AllIcons.Nodes.Function;
  }

  @Override
  public String toString() {
    return "XsltFunction: " + getName();
  }

  @Override
  public Function getDeclaration() {
    return this;
  }

  @Override
  public String buildSignature() {
    return getFunction().buildSignature();
  }

  @Override
  public String getName() {
    return getFunction().getName();
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    return super.setName(getQName().getPrefix() + ":" + name);
  }

  @Override
  public @Nullable @NlsSafe String getPresentableText() {
    final Function function = getFunction();
    return function.buildSignature() + ": " + function.getReturnType().getName();
  }

  @Override
  public Parameter @NotNull [] getParameters() {
    return getFunction().getParameters();
  }

  @Override
  public @NotNull XPathType getReturnType() {
    return getFunction().getReturnType();
  }

  @Override
  public int getMinArity() {
    return getFunction().getMinArity();
  }

  @Override
  public void accept(@NotNull XPathElementVisitor visitor) {
    visitor.visitXPathFunction(this);
  }
}
