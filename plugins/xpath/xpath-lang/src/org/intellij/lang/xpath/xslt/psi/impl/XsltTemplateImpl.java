/*
 * Copyright 2005 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.xpath.xslt.psi.impl;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import icons.XpathIcons;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.impl.ResolveUtil;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.XsltParameter;
import org.intellij.lang.xpath.xslt.psi.XsltTemplate;
import org.intellij.lang.xpath.xslt.util.ParamMatcher;
import org.intellij.lang.xpath.xslt.util.QNameUtil;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.xml.namespace.QName;
import java.util.Arrays;

final class XsltTemplateImpl extends XsltElementImpl implements XsltTemplate {
  XsltTemplateImpl(XmlTag target) {
    super(target);
  }

  @Override
  public Icon getIcon(int flags) {
    return XpathIcons.Template;
  }

  @Override
  public String getPresentableText() {
    return buildSignature();
  }

  public @NlsSafe String buildSignature() {
    final StringBuilder sb = new StringBuilder(getName());
    final XsltParameter[] parameters = getParameters();
    if (parameters.length > 0) {
      sb.append(" (");
      for (int i1 = 0; i1 < parameters.length; i1++) {
        if (i1 > 0) {
          sb.append(", ");
        }
        if (parameters[i1].hasDefault()) {
          sb.append("[").append(parameters[i1].getName()).append("]");
        }
        else {
          sb.append(parameters[i1].getName());
        }
      }
      sb.append(")");
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    return "XsltTemplate: " + getName();
  }

  @Override
  public XsltParameter @NotNull [] getParameters() {
    final PsiElement[] elements = ResolveUtil.collect(new ParamMatcher(getTag(), null));

    return Arrays.copyOf(elements, elements.length, XsltParameter[].class);
  }

  @Override
  public @Nullable XsltParameter getParameter(String name) {
    return (XsltParameter)ResolveUtil.resolve(new ParamMatcher(getTag(), name));
  }

  @Override
  public @Nullable XPathExpression getMatchExpression() {
    return XsltCodeInsightUtil.getXPathExpression(this, "match");
  }

  @Override
  public @Nullable QName getMode() {
    final String mode = getTag().getAttributeValue("mode");
    return mode != null ? QNameUtil.createQName(mode, getTag()) : null;
  }

  @Override
  public boolean isAbstract() {
    return "true".equals(getTag().getAttributeValue("abstract", XsltSupport.PLUGIN_EXTENSIONS_NS));
  }
}
