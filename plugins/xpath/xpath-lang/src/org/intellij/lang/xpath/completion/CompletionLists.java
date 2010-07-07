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
package org.intellij.lang.xpath.completion;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.Icons;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.xpath.XPathFile;
import org.intellij.lang.xpath.XPathTokenTypes;
import org.intellij.lang.xpath.context.ContextProvider;
import org.intellij.lang.xpath.context.NamespaceContext;
import org.intellij.lang.xpath.context.VariableContext;
import org.intellij.lang.xpath.context.functions.Function;
import org.intellij.lang.xpath.psi.*;

import javax.xml.namespace.QName;
import java.util.*;

public class CompletionLists {
    public static final String INTELLIJ_IDEA_RULEZ = "IntellijIdeaRulezzz";

    private CompletionLists() {
    }

    public static final Set<String> NODE_TYPE_FUNCS = new HashSet<String>(Arrays.asList(
            "text", "node", "comment", "processing-instruction"
    ));

    public static final Set<String> OPERATORS = new HashSet<String>(Arrays.asList(
            "mul", "div", "and", "or"
    ));

    public static final Set<String> AXIS_NAMES = new HashSet<String>(Arrays.asList(
            "ancestor",
            "ancestor-or-self",
            "attribute",
            "child",
            "descendant",
            "descendant-or-self",
            "following",
            "following-sibling",
            "namespace",
            "parent",
            "preceding",
            "preceding-sibling",
            "self"
    ));

    public static Collection<Lookup> getFunctionCompletions(XPathElement element) {
        final XPathFile xpathFile = (XPathFile)element.getContainingFile();

        final ContextProvider contextProvider = ContextProvider.getContextProvider(xpathFile);
        final NamespaceContext nsContext = contextProvider.getNamespaceContext();

        final Map<QName, ? extends Function> functions = contextProvider.getFunctionContext().getFunctions();
        final List<Lookup> lookups = new ArrayList<Lookup>(functions.size());
        for (QName f : functions.keySet()) {
            final Function functionDecl = functions.get(f);
            final String p;
            if (nsContext != null) {
                p = makePrefix(nsContext.getPrefixForURI(f.getNamespaceURI(), PsiTreeUtil.getContextOfType(element, XmlElement.class, true)));
            } else {
                p = "";
            }
            lookups.add(FunctionLookup.newFunctionLookup(p + f.getLocalPart(), functionDecl));
        }
        return lookups;
    }

    public static Collection<Lookup> getVariableCompletions(XPathElement reference) {
        final ContextProvider contextProvider = ContextProvider.getContextProvider(reference);
        final VariableContext resolver = contextProvider.getVariableContext();
        if (resolver != null) {
            final Object[] variablesInScope = resolver.getVariablesInScope(reference);
            final List<Lookup> lookups = new ArrayList<Lookup>(variablesInScope.length);
            for (final Object o : variablesInScope) {
                if (o instanceof PsiNamedElement) {
                    final String type;
                    if (o instanceof XPathVariable) {
                        final XPathType t = ((XPathVariable)o).getType();
                        if (t != XPathType.UNKNOWN) {
                            type = t.getName();
                        } else {
                            type = "";
                        }
                    } else {
                        type = "";
                    }
                    final String name = ((PsiNamedElement)o).getName();
                    lookups.add(new VariableLookup("$" + name, type, ((PsiNamedElement)o).getIcon(0), (PsiElement)o));
                } else {
                    lookups.add(new VariableLookup("$" + String.valueOf(o), "", Icons.VARIABLE_ICON));
                }
            }
            return lookups;
        } else {
            return Collections.emptySet();
        }
    }

    public static Collection<Lookup> getNodeTestCompletions(final XPathNodeTest element) {
        if (!element.isNameTest()) {
            return Collections.emptyList();
        }

        final PrefixedName prefixedName = element.getQName();
        assert prefixedName != null;

        final String canonicalText = prefixedName.toString();
        final String suffix = canonicalText.substring(canonicalText.indexOf(INTELLIJ_IDEA_RULEZ));

        final XPathAxisSpecifier axisSpecifier = element.getStep().getAxisSpecifier();

        final ContextProvider contextProvider = ContextProvider.getContextProvider(element);
        final XmlElement context = contextProvider.getContextElement();

        final boolean insidePrefix = suffix.indexOf(INTELLIJ_IDEA_RULEZ + ":") != -1;

        final Set<Lookup> list = new HashSet<Lookup>();
        addNameCompletions(contextProvider, element, list);

        final String namespacePrefix = prefixedName.getPrefix();
        if (namespacePrefix == null || insidePrefix) {
            addNamespaceCompletions(contextProvider.getNamespaceContext(), list, context);
        }

        final XPathNodeTest.PrincipalType principalType = addContextNames(element, contextProvider, prefixedName, list);

        if (namespacePrefix == null && !insidePrefix) {
            if (axisSpecifier == null || axisSpecifier.isDefaultAxis()) {
                list.addAll(getAxisCompletions());

                // wow, this code sux. find a better implementation
                PsiElement sibling = element.getParent().getPrevSibling();
                while (sibling instanceof PsiWhiteSpace) {
                    sibling = sibling.getPrevSibling();
                }

                boolean check = sibling != null;
                if (!check) {
                    XPathLocationPath lp = null;
                    do {
                        lp = PsiTreeUtil.getParentOfType(lp == null ? element : lp, XPathLocationPath.class, true);
                    } while (lp != null && lp.getPrevSibling() == null);

                    check = lp == null || (sibling = lp.getPrevSibling()) != null;
                }
                if (check) {
                    if (sibling instanceof XPathToken && XPathTokenTypes.PATH_OPS.contains(((XPathToken)sibling).getTokenType())) {
                        // xx/yy<caret> : prevSibl = /
                    } else {
                        list.addAll(getFunctionCompletions(element));
                        list.addAll(getVariableCompletions(element));
                    }
                }
            }
            if (principalType == XPathNodeTest.PrincipalType.ELEMENT) {
                list.addAll(getNodeTypeCompletions());
            }
        }

        return list;
    }

    private static XPathNodeTest.PrincipalType addContextNames(XPathNodeTest element, ContextProvider contextProvider, PrefixedName prefixedName, Set<Lookup> list) {
        final NamespaceContext namespaceContext = contextProvider.getNamespaceContext();
        final XmlElement context = contextProvider.getContextElement();

        final XPathNodeTest.PrincipalType principalType = element.getPrincipalType();
        if (principalType == XPathNodeTest.PrincipalType.ELEMENT) {
            final Set<javax.xml.namespace.QName> elementNames = contextProvider.getElements(false);
            if (elementNames != null) {
                for (javax.xml.namespace.QName pair : elementNames) {
                    if ("*".equals(pair.getLocalPart())) continue;

                    if (namespaceMatches(prefixedName, pair.getNamespaceURI(), namespaceContext, context)) {
                        if (prefixedName.getPrefix() == null && namespaceContext != null) {
                            final String p = namespaceContext.getPrefixForURI(pair.getNamespaceURI(), context);
                            list.add(new NodeLookup(makePrefix(p) + pair.getLocalPart(), XPathNodeTest.PrincipalType.ELEMENT));
                        } else {
                            list.add(new NodeLookup(pair.getLocalPart(), XPathNodeTest.PrincipalType.ELEMENT));
                        }
                    }
                }
            }
        } else if (principalType == XPathNodeTest.PrincipalType.ATTRIBUTE) {
            final Set<javax.xml.namespace.QName> attributeNames = contextProvider.getAttributes(false);
            if (attributeNames != null) {
                for (javax.xml.namespace.QName pair : attributeNames) {
                    if ("*".equals(pair.getLocalPart())) continue;

                    if (namespaceMatches(prefixedName, pair.getNamespaceURI(), namespaceContext, context)) {
                        if (prefixedName.getPrefix() == null && namespaceContext != null) {
                            final String p = namespaceContext.getPrefixForURI(pair.getNamespaceURI(), context);
                            list.add(new NodeLookup(makePrefix(p) + pair.getLocalPart(), XPathNodeTest.PrincipalType.ATTRIBUTE));
                        } else {
                            list.add(new NodeLookup(pair.getLocalPart(), XPathNodeTest.PrincipalType.ATTRIBUTE));
                        }
                    }
                }
            }
        }
        return principalType;
    }

    private static String makePrefix(String p) {
        return (p != null && p.length() > 0 ? p + ":" : "");
    }

    private static void addNamespaceCompletions(NamespaceContext namespaceContext, Set<Lookup> list, XmlElement context) {
        if (namespaceContext != null) {
            final Collection<String> knownPrefixes = namespaceContext.getKnownPrefixes(context);
            for (String prefix : knownPrefixes) {
                if (prefix != null && prefix.length() > 0) {
                    list.add(new NamespaceLookup(prefix));
                }
            }
        }
    }

    private static void addNameCompletions(ContextProvider contextProvider, final XPathNodeTest element, final Set<Lookup> list) {
      final PrefixedName prefixedName = element.getQName();
      final XPathNodeTest.PrincipalType principalType = element.getPrincipalType();

      final Set<PsiFile> files = new HashSet<PsiFile>();
      final XPathFile file = (XPathFile)element.getContainingFile();
      files.add(file);
      ContainerUtil.addAll(files, contextProvider.getRelatedFiles(file));

      for (PsiFile xpathFile : files) {
        xpathFile.accept(new PsiRecursiveElementVisitor() {
          public void visitElement(PsiElement e) {
            if (e instanceof XPathNodeTest) {
              final XPathNodeTest nodeTest = (XPathNodeTest)e;

              final XPathNodeTest.PrincipalType _principalType = nodeTest.getPrincipalType();
              if (_principalType == principalType) {
                final PrefixedName _prefixedName = nodeTest.getQName();
                if (_prefixedName != null && prefixedName != null) {
                  final String localName = _prefixedName.getLocalName();
                  if (!"*".equals(localName) && localName.indexOf(INTELLIJ_IDEA_RULEZ) == -1) {
                    if (Comparing.equal(_prefixedName.getPrefix(), prefixedName.getPrefix())) {
                      list.add(new NodeLookup(localName, _principalType));
                    }
                    else if (prefixedName.getPrefix() == null) {
                      list.add(new NodeLookup(_prefixedName.toString(), _principalType));
                    }
                  }
                }
              }
            }
            super.visitElement(e);
          }
        });
      }
    }

    private static boolean namespaceMatches(PrefixedName prefixedName, String uri, NamespaceContext namespaceContext, XmlElement context) {
        if (namespaceContext == null || prefixedName.getPrefix() == null || uri == null) return true;
        return uri.equals(namespaceContext.getNamespaceURI(prefixedName.getPrefix(), context));
    }

    public static Collection<Lookup> getNodeTypeCompletions() {
        final List<Lookup> lookups = new ArrayList<Lookup>(NODE_TYPE_FUNCS.size());
        for (String f : NODE_TYPE_FUNCS) {
            if (f.equals("processing-instruction")) {
                lookups.add(new FunctionLookup(f, f + "(pi-target?)", null));
            } else {
                lookups.add(new FunctionLookup(f, f + "()", null));
            }
        }
        return lookups;
    }

    public static Collection<Lookup> getAxisCompletions() {
        final ArrayList<Lookup> lookups = new ArrayList<Lookup>(AXIS_NAMES.size());
        for (String s : AXIS_NAMES) {
            lookups.add(new AxisLookup(s));
        }
        return lookups;
    }

    @SuppressWarnings({"RawUseOfParameterizedType"})
    public static Class[] getAllInterfaces(Class<?> clazz) {
        Set<Class> set = new HashSet<Class>();
        do {
          ContainerUtil.addAll(set, clazz.getInterfaces());
          clazz = clazz.getSuperclass();
        } while (clazz != null);
        return set.toArray(new Class[set.size()]);
    }
}
