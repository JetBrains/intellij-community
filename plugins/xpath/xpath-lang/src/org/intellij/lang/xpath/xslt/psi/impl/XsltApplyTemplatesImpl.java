// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.xpath.xslt.psi.impl;

import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.xslt.psi.XsltApplyTemplates;
import org.intellij.lang.xpath.xslt.util.QNameUtil;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;
import org.jetbrains.annotations.Nullable;

import javax.xml.namespace.QName;

final class XsltApplyTemplatesImpl extends XsltTemplateInvocationBase implements XsltApplyTemplates {
  XsltApplyTemplatesImpl(XmlTag target) {
    super(target);
  }

  @Override
  public String toString() {
    return "XsltApplyTemplates[" + getSelect() + "]";
  }

  @Override
  public @Nullable XPathExpression getSelect() {
    return XsltCodeInsightUtil.getXPathExpression(this, "select");
  }

  @Override
  public QName getMode() {
    final String mode = getTag().getAttributeValue("mode");
    return mode != null ? QNameUtil.createQName(mode, getTag()) : null;
  }
}