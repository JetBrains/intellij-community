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

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.Language;
import com.intellij.psi.xml.XmlElement;
import org.intellij.lang.xpath.XPathFileType;
import org.intellij.lang.xpath.context.ContextProvider;
import org.intellij.lang.xpath.context.NamespaceContext;
import org.intellij.lang.xpath.psi.PrefixedName;
import org.intellij.lang.xpath.psi.XPathNodeTest;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.namespace.QName;
import java.text.MessageFormat;
import java.util.Set;

public class CheckNodeTest extends XPathInspection {
    @NonNls
    private static final String SHORT_NAME = "CheckNodeTest";

    protected Visitor createVisitor(InspectionManager manager, boolean isOnTheFly) {
        return new MyVisitor(manager, isOnTheFly);
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

    protected boolean acceptsLanguage(Language language) {
      return language == XPathFileType.XPATH.getLanguage() || language == XPathFileType.XPATH2.getLanguage();
    }

    final static class MyVisitor extends Visitor {
        MyVisitor(InspectionManager manager, boolean isOnTheFly) {
            super(manager, isOnTheFly);
        }

        protected void checkNodeTest(XPathNodeTest nodeTest) {
            final ContextProvider contextProvider = ContextProvider.getContextProvider(nodeTest.getContainingFile());
            final XmlElement contextNode = contextProvider.getContextElement();
            final NamespaceContext namespaceContext = contextProvider.getNamespaceContext();
            if (namespaceContext == null) return;

            if (nodeTest.isNameTest() && contextNode != null) {
                final PrefixedName prefixedName = nodeTest.getQName();
                assert prefixedName != null;
                if (!"*".equals(prefixedName.getLocalName()) && !"*".equals(prefixedName.getPrefix())) {
                    boolean found;

                    if (nodeTest.getPrincipalType() == XPathNodeTest.PrincipalType.ELEMENT) {
                        final Set<QName> elementNames = contextProvider.getElements(true);
                        if (elementNames != null) {
                            found = false;
                            for (QName pair : elementNames) {
                              if (matches(nodeTest.getQName(), pair, namespaceContext, contextNode, true)) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                registerProblem(contextProvider, prefixedName, nodeTest, "element");
                            }
                        }
                    } else if (nodeTest.getPrincipalType() == XPathNodeTest.PrincipalType.ATTRIBUTE) {
                        final Set<QName> attributeNames = contextProvider.getAttributes(true);
                        if (attributeNames != null) {
                            found = false;
                            for (QName pair : attributeNames) {
                                if (matches(nodeTest.getQName(), pair, namespaceContext, contextNode, false)) {
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
            addProblem(myManager.createProblemDescriptor(nodeTest, "<html>Unknown " + type + " name " + name + "</html>", myOnTheFly, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        }

        private static boolean matches(@Nullable PrefixedName prefixedName,
                                       QName element,
                                       NamespaceContext namespaceContext,
                                       XmlElement context,
                                       boolean allowDefaultNamespace)
        {
            if (prefixedName == null) return false;

            boolean b = prefixedName.getLocalName().equals(element.getLocalPart()) || "*".equals(element.getLocalPart());

            final String prefix = prefixedName.getPrefix();
            if (prefix != null) {
              if (!"*".equals(prefix)) {
                final String namespaceURI = namespaceContext.getNamespaceURI(prefix, context);
                b = b && element.getNamespaceURI().equals(namespaceURI);
              }
            } else if (allowDefaultNamespace) {
              final String namespaceURI = namespaceContext.getDefaultNamespace(context);
              b = b && (element.getNamespaceURI().equals(namespaceURI) || (element.getNamespaceURI().length() == 0 && namespaceURI == null));
            } else {
              b = b && element.getNamespaceURI().length() == 0;
            }
          return b;
        }
    }
}
