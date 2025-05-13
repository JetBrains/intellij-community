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

import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.xslt.psi.XsltWithParam;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;

final class XsltWithParamImpl extends XsltElementImpl implements XsltWithParam {
  XsltWithParamImpl(XmlTag target) {
    super(target);
  }

  @Override
  public String getParamName() {
    return getName();
  }

  @Override
  public XPathExpression getExpression() {
    return XsltCodeInsightUtil.getXPathExpression(this, "select");
  }

  @Override
  public String toString() {
    return "XsltWithParam: " + getName();
  }
}
