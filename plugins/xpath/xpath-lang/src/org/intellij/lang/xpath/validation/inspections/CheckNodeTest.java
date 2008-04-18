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
package org.intellij.lang.xpath.validation.inspections;

import org.intellij.lang.xpath.context.ContextProvider;
import org.intellij.lang.xpath.context.NamespaceContext;
import org.intellij.lang.xpath.psi.PrefixedName;
import org.intellij.lang.xpath.psi.XPathNodeTest;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.xml.namespace.QName;
import java.util.Set;
import java.text.MessageFormat;

public class CheckNodeTest extends XPathInspection {
    @NonNls
    private static final String SHORT_NAME = "CheckNodeTest";

    protected Visitor createVisitor(InspectionManager manager) {
        return new MyVisitor(manager);
    }

    @NotNull
    public String getDisplayName() {
        return "Check Node Test";
    }

    @NotNull
    @NonNls
    public String getShortName() {
        return SHORT_NAME;
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    final static class MyVisitor extends Visitor {
        MyVisitor(InspectionManager manager) {
            super(manager);
        }

        protected void checkNodeTest(XPathNodeTest nodeTest) {
            final ContextProvider contextProvider = ContextProvider.getContextProvider(nodeTest.getContainingFile());
            final XmlElement contextNode = contextProvider.getContextElement();
            final NamespaceContext namespaceContext = contextProvider.getNamespaceContext();
            if (namespaceContext == null) return;

            if (nodeTest.isNameTest() && contextNode != null) {
                final PrefixedName prefixedName = nodeTest.getQName();
                assert prefixedName != null;
                if (!"*".equals(prefixedName.getLocalName())) {
                    boolean found;

                    if (nodeTest.getPrincipalType() == XPathNodeTest.PrincipalType.ELEMENT) {
                        final Set<QName> elementNames = contextProvider.getElements(true);
                        if (elementNames != null) {
                            found = false;
                            for (javax.xml.namespace.QName pair : elementNames) {
                                if (matches(nodeTest.getQName(), pair, namespaceContext, contextNode)) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                registerProblem(contextProvider, prefixedName, nodeTest, "element");
                            }
                        }
                    } else if (nodeTest.getPrincipalType() == XPathNodeTest.PrincipalType.ATTRIBUTE) {
                        final Set<javax.xml.namespace.QName> attributeNames = contextProvider.getAttributes(true);
                        if (attributeNames != null) {
                            found = false;
                            for (javax.xml.namespace.QName pair : attributeNames) {
                                if (matches(nodeTest.getQName(), pair, namespaceContext, contextNode)) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                registerProblem(contextProvider, prefixedName, nodeTest, "attribute");
                            }
                        }
                    }
                }
            }
        }

        private void registerProblem(ContextProvider contextProvider, PrefixedName prefixedName, XPathNodeTest nodeTest, String type) {
            final QName qName = contextProvider.getQName(prefixedName, nodeTest);
            final String name;
            if (qName != null) {
                final String pattern;
                if (!"".equals(qName.getNamespaceURI())) {
                    pattern = "''<b>{0}</b>'' (<i>{1}</i>)";
                } else {
                    pattern = "''<b>{0}</b>''";
                }
                name = MessageFormat.format(pattern, qName.getLocalPart(), qName.getNamespaceURI());
            } else {
                name = MessageFormat.format("''<b>{0}</b>''", prefixedName.getLocalName());
            }

            final LocalQuickFix[] fixes = contextProvider.getQuickFixFactory().createUnknownNodeTestFixes(nodeTest);
            addProblem(myManager.createProblemDescriptor(nodeTest, "<html>Unknown " + type + " name " + name + "</html>",
                    fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        }

        private static boolean matches(PrefixedName prefixedName, QName element, NamespaceContext namespaceContext, XmlElement context) {
            boolean b = prefixedName.getLocalName().equals(element.getLocalPart()) || "*".equals(element.getLocalPart());
            if (prefixedName.getPrefix() != null) {
                b = b && element.getNamespaceURI().equals(namespaceContext.getNamespaceURI(prefixedName.getPrefix(), context));
            } else {
                b = b && element.getNamespaceURI().equals("");
            }
            return b;
        }
    }
}
