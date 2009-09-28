/*
 * Copyright 2002-2005 Sascha Weinreuter
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
package org.intellij.plugins.xpathView.support.jaxen;

import org.intellij.plugins.xpathView.XPathExpressionGenerator;
import org.intellij.plugins.xpathView.support.XPathSupport;
import org.intellij.plugins.xpathView.support.jaxen.extensions.FunctionImplementation;
import org.intellij.plugins.xpathView.util.Namespace;
import org.intellij.plugins.xpathView.util.NamespaceCollector;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import org.intellij.lang.xpath.context.functions.Function;
import org.intellij.lang.xpath.context.functions.XPathFunctionProvider;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.jaxen.JaxenException;
import org.jaxen.SimpleNamespaceContext;
import org.jaxen.UnresolvableException;
import org.jaxen.XPath;
import org.jaxen.XPathFunctionContext;

import javax.xml.namespace.QName;
import java.util.Collection;
import java.util.List;
import java.util.Map;

class XPathSupportImpl extends XPathSupport {
    private static final Logger LOG = Logger.getInstance("org.intellij.plugins.xpathView.support.jaxen.XPathSupportImpl");

    public XPath createXPath(@NotNull XmlFile file, String expression) throws JaxenException {
        final PsiXPath xpath = new PsiXPath(file, expression);
        xpath.setFunctionContext(new MyXPathFunctionContext());
        xpath.setNamespaceContext(new MySimpleNamespaceContext(NamespaceCollector.findNamespaces(file)));

        return xpath;
    }

    public XPath createXPath(@Nullable XmlFile psiFile, String expression, @NotNull Collection<Namespace> namespaces) throws JaxenException {
        final PsiXPath xpath = new PsiXPath(psiFile, expression);
        xpath.setFunctionContext(new MyXPathFunctionContext());
        xpath.setNamespaceContext(new MySimpleNamespaceContext(NamespaceCollector.convert(namespaces)));

        return xpath;
    }

    public String getUniquePath(XmlElement element, XmlTag context) {
        return XPathExpressionGenerator.getUniquePath(element, context);
    }

    public String getPath(XmlElement element, XmlTag context) {
        return XPathExpressionGenerator.getPath(element, context);
    }

    private static class MySimpleNamespaceContext extends SimpleNamespaceContext {
        public MySimpleNamespaceContext(Map<String, String> map) {
            super(map);
        }

        public String translateNamespacePrefixToUri(String prefix) {
            final String uri = super.translateNamespacePrefixToUri(prefix);
            // avoid matching of undefined prefixes on default namespace
            if (prefix != null && prefix.length() > 0) {
                if (uri == null) {
                    LOG.debug("Undefined prefix: " + prefix);
                    return "urn:xpathview:undefined-namespace";
                } else {
                    return uri;
                }
            } else {
                // Don't resolve empty prefix to anything. This is called by Jaxen for internal function resolving
                // and this will break when anything else than null is returned.
                LOG.debug("Empty prefix, returning null (uri=" + uri + ")");
                return null;
            }
        }
    }

    private static class MyXPathFunctionContext extends XPathFunctionContext {
        public MyXPathFunctionContext() {
            final List<Pair<QName,? extends Function>> functions = XPathFunctionProvider.getAvailableFunctions(TYPE);
            for (Pair<QName,? extends Function> function : functions) {
                final Function f = function.getSecond();
                final QName name = function.getFirst();
                final String namespaceURI = "".equals(name.getNamespaceURI()) ? null : name.getNamespaceURI();
                if (f instanceof FunctionImplementation) {
                    registerFunction(namespaceURI, name.getLocalPart(), ((FunctionImplementation)f).getImplementation());
                } else {
                    try {
                        super.getFunction(namespaceURI, name.getPrefix(), name.getLocalPart());
                    } catch (UnresolvableException e) {
                        LOG.info("Warning: Unresolvable extension function: " + name + " - " + f);
                    }
                }
            }
        }
    }
}
