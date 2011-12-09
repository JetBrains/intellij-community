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

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.xpath.psi.impl.ResolveUtil;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.*;
import org.intellij.lang.xpath.xslt.util.IncludeAwareMatcher;
import org.intellij.lang.xpath.xslt.util.ParamMatcher;
import org.intellij.lang.xpath.xslt.util.TemplateMatcher;
import org.jetbrains.annotations.NotNull;

public class XsltStylesheetImpl extends XsltElementImpl implements XsltStylesheet {

  public XsltStylesheetImpl(XmlTag target) {
    super(target);
  }

  @NotNull
  public XsltParameter[] getParameters() {
    return convertArray(ResolveUtil.collect(new ParamMatcher(getTag(), null)), XsltParameter.class);
  }

  @NotNull
  public XsltVariable[] getVariables() {
    return convertArray(ResolveUtil.collect(new ParamMatcher(getTag(), null) {
      protected boolean isApplicable(XmlTag tag) {
        return XsltSupport.isVariable(tag);
      }
    }), XsltVariable.class);
  }

  @NotNull
  public XsltTemplate[] getTemplates() {
    final XmlDocument document = PsiTreeUtil.getParentOfType(getTag(), XmlDocument.class);
    return convertArray(ResolveUtil.collect(new TemplateMatcher(document)), XsltTemplate.class);
  }

  @NotNull
  @Override
  public XsltFunction[] getFunctions() {
    final XmlDocument document = PsiTreeUtil.getParentOfType(getTag(), XmlDocument.class);
    return convertArray(ResolveUtil.collect(new FunctionMatcher(document)), XsltFunction.class);
  }

  @Override
  public String toString() {
    return "XsltStylesheet";
  }

  private class FunctionMatcher extends IncludeAwareMatcher {
    public FunctionMatcher(XmlDocument document) {
      super(document);
    }

    protected boolean matches(XmlTag element) {
      return XsltSupport.isFunction(element);
    }

    protected ResolveUtil.Matcher changeDocument(XmlDocument document) {
      return new FunctionMatcher(document);
    }

    public ResolveUtil.Matcher variantMatcher() {
      return new FunctionMatcher(myDocument);
    }
  }
}
