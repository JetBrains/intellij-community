/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import icons.XpathIcons;
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

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 11.01.11
*/
public class XsltFunctionImpl extends XsltElementImpl implements XsltFunction, ItemPresentation {
  private static final NotNullFunction<XmlTag,Parameter> PARAM_MAPPER = new NotNullFunction<XmlTag, Parameter>() {
    @NotNull
    @Override
    public Parameter fun(XmlTag param) {
      final XPathType type = XsltCodeInsightUtil.getDeclaredType(param);
      return new Parameter(type != null ? type : XPath2Type.SEQUENCE, Parameter.Kind.REQUIRED);
    }
  };

  protected XsltFunctionImpl(XmlTag target) {
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
    return XpathIcons.Function;
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

  @Nullable
  public String getPresentableText() {
    final Function function = getFunction();
    return function != null ? function.buildSignature() +
            ": " + function.getReturnType().getName() : null;
  }

  @NotNull
  @Override
  public Parameter[] getParameters() {
    return getFunction().getParameters();
  }

  @NotNull
  @Override
  public XPathType getReturnType() {
    return getFunction().getReturnType();
  }

  @Override
  public int getMinArity() {
    return getFunction().getMinArity();
  }

  public void accept(@NotNull XPathElementVisitor visitor) {
    visitor.visitXPathFunction(this);
  }
}
