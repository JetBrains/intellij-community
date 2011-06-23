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

import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.PlatformIcons;
import org.intellij.lang.xpath.psi.XPathElementVisitor;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathType;
import org.intellij.lang.xpath.psi.XPathVariableReference;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.XsltVariable;
import org.intellij.lang.xpath.xslt.util.QNameUtil;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class XsltVariableImpl extends XsltElementImpl implements XsltVariable {

    XsltVariableImpl(XmlTag target) {
        super(target);
    }

    @Override
    public Icon getIcon(int i) {
        return PlatformIcons.VARIABLE_ICON;
    }

    @NotNull
    public XPathType getType() {
        final XPathType declaredType = XsltCodeInsightUtil.getDeclaredType(getTag());
        if (declaredType != null) {
          return declaredType;
        }

        final XmlAttribute attr = getTag().getAttribute("type", XsltSupport.PLUGIN_EXTENSIONS_NS);
        if (attr != null) {
            return XPathType.fromString(attr.getValue());
        }
        final XPathExpression value = getValue();
        if (value instanceof XPathVariableReference) {
            // recursive reference <xsl:variable name="foo" select="$foo" />
            final XPathVariableReference reference = (XPathVariableReference)value;
            if (reference.resolve() == this) {
                return XPathType.UNKNOWN;
            }
        }
        return value != null ? value.getType() : XPathType.UNKNOWN;
    }

    @Nullable
    public XPathExpression getValue() {
        return XsltCodeInsightUtil.getXPathExpression(this, "select");
    }

    @NotNull
    @Override
    public final SearchScope getUseScope() {
        return XsltSupport.isTopLevelElement(getTag()) ? getDefaultUseScope() : getLocalUseScope();
    }

    @NotNull
    protected SearchScope getLocalUseScope() {
        return new LocalSearchScope(getTag().getParentTag());
    }

    @NotNull
    protected SearchScope getDefaultUseScope() {
        return super.getUseScope();
    }

    @Override
    public String toString() {
        return "XPathVariable(XSLT): " + getTag().getValue();
    }

    public boolean isVoid() {
        final String name = getName();
        return name != null && "type:void".equals(QNameUtil.createQName(name, getTag()).getNamespaceURI());
    }

  public void accept(@NotNull XPathElementVisitor visitor) {
    visitor.visitXPathVariable(this);
  }
}
