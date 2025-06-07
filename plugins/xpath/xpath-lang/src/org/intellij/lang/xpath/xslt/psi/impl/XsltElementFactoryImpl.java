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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.XsltElement;
import org.intellij.lang.xpath.xslt.psi.XsltElementFactory;
import org.jetbrains.annotations.Nullable;

class XsltElementFactoryImpl extends XsltElementFactory {
    private static final Key<Pair<ASTNode, XsltElement>> WRAPPER = Key.create("WRAPPER");

  @Override
    public <T extends XsltElement> T wrapElement(XmlTag target, Class<T> clazz) {
        assert target.isValid();

        final Pair<ASTNode, XsltElement> wrapper = target.getUserData(WRAPPER);
        final ASTNode tagNode = target.getNode();
        final ASTNode nameNode = tagNode != null ? XmlChildRole.START_TAG_NAME_FINDER.findChild(tagNode) : null;
        if (wrapper != null) {
            if (wrapper.getFirst() == nameNode) {
                if (wrapper.second.isValid() && clazz.isAssignableFrom(wrapper.second.getClass())) {
                    return clazz.cast(wrapper.second);
                }
            }
        }
        assert XsltSupport.isXsltTag(target) : "Not an XSLT tag: {" + target.getNamespace() + "}" + target.getName();

        final XsltElement element;
        if (XsltSupport.isTemplate(target, false)) {
            element = new XsltTemplateImpl(target);
        } else if (XsltSupport.isVariable(target)) {
            element = new XsltVariableImpl(target);
        } else if (XsltSupport.isParam(target)) {
            element = new XsltParameterImpl(target);
        } else if (XsltSupport.isTemplateCall(target)) {
            element = new XsltCallTemplateImpl(target);
        } else if (XsltSupport.isApplyTemplates(target)) {
            element = new XsltApplyTemplatesImpl(target);
        } else if ("with-param".equals(target.getLocalName())) {
            element = new XsltWithParamImpl(target);
        } else if (XsltSupport.isXsltRootTag(target)) {
            element = new XsltStylesheetImpl(target);
        } else if (XsltSupport.isFunction(target)) {
            element = new XsltFunctionImpl(target);
        } else {
            element = new DummyElementImpl(target);
        }

        if (!(element instanceof DummyElementImpl)) {
            target.putUserData(WRAPPER, Pair.create(nameNode, element));
        }
        return clazz.cast(element);
    }

    private static final class DummyElementImpl extends XsltElementImpl {
        DummyElementImpl(XmlTag target) {
            super(target);
        }

        @Override
        public @Nullable String toString() {
            return "Unrecognized tag: " + getTag().getName();
        }
    }
}
