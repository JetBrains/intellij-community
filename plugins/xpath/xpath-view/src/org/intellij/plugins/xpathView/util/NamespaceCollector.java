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
package org.intellij.plugins.xpathView.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import javax.xml.namespace.QName;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Helper class to collect all used namespaces and their prefixes from an xml document
 */
public class NamespaceCollector extends XmlRecursiveElementVisitor {
    private static final Logger LOG = Logger.getInstance("org.intellij.plugins.xpathView.util.NamespaceCollector");

    public static class CollectedInfo {
        public final Set<Namespace> namespaces;
        public final Set<QName> elements;
        public final Set<QName> attributes;

        CollectedInfo(Set<Namespace> namespaces, Set<QName> elements, Set<QName> attributes) {
            this.namespaces = Collections.unmodifiableSet(namespaces);
            this.elements = Collections.unmodifiableSet(elements);
            this.attributes = Collections.unmodifiableSet(attributes);
        }
    }

    private final Set<Namespace> namespaces = new LinkedHashSet<>();
    private final Set<QName> elements = new HashSet<>(64);
    private final Set<QName> attributes = new HashSet<>(64);

    private NamespaceCollector() {

    }

    public Set<Namespace> getNamespaces() {
        return namespaces;
    }

    @Override
    public void visitXmlAttribute(XmlAttribute xmlAttribute) {
        if (xmlAttribute.isNamespaceDeclaration()) {
            LOG.debug("Namespace: " + xmlAttribute.getLocalName() + " => " + xmlAttribute.getValue());
            addNamespace(xmlAttribute.getLocalName(), xmlAttribute.getValue());
        } else {
            addAttribute(xmlAttribute);
        }
    }

    @Override
    public void visitXmlTag(XmlTag tag) {
        final Map<String, String> namespaceDeclarations = tag.getLocalNamespaceDeclarations();
        final Set<String> localPrefixes = namespaceDeclarations.keySet();
        for (String prefix : localPrefixes) {
            // More namespace fun...
            // [IDEA-5883] XmlTag.getLocalNamespaceDeclarations() return "xmlns" for default ("") namespace
            addNamespace("xmlns".equals(prefix) ? "" : prefix, namespaceDeclarations.get(prefix));
        }
        addElement(tag);
        super.visitXmlTag(tag);
    }

    private void addAttribute(XmlAttribute xmlAttribute) {
        final String namespace = xmlAttribute.getNamespace();
        final String localName = xmlAttribute.getLocalName();
        // [IDEA-5206] XmlAttribute.getNamespace() returns namespace of parent element if no prefix in attribute name. That's wrong
        final String name = xmlAttribute.getName();
        if (name.indexOf(':') == -1) {
            attributes.add(new QName("", localName));
        } else {
            attributes.add(new QName(namespace, localName));
        }
    }

    private void addElement(XmlTag tag) {
        final String uri = tag.getNamespace();
        final String prefix = tag.getNamespacePrefix();
        // [IDEA-5885] IDEA assigns System-URI of DTD as namespace.
        if (MyPsiUtil.isInDeclaredNamespace(tag, uri, prefix)) {
            elements.add(new QName(uri, tag.getLocalName(), prefix));
        } else {
            elements.add(new QName("", tag.getLocalName(), ""));
        }
    }

    private void addNamespace(final String prefix, final String value) {
        if (value.length() > 0) {
            final Namespace namespace = new Namespace(prefix, value);
            namespaces.add(namespace);
        }
    }

    public static Set<Namespace> findAllNamespaces(final XmlFile psiFile) {
        final NamespaceCollector namespaceCollector = new NamespaceCollector();
        final XmlDocument document = psiFile.getDocument();
        if (document != null) {
            document.accept(namespaceCollector);
        }
        return namespaceCollector.namespaces;
    }

    public static CollectedInfo empty() {
        //noinspection unchecked
        return new CollectedInfo(Collections.<Namespace>emptySet(), Collections.<QName>emptySet(), Collections.<QName>emptySet());
    }
    
    public static CollectedInfo collectInfo(final XmlFile psiFile) {
        final NamespaceCollector namespaceCollector = new NamespaceCollector();
        final XmlDocument document = psiFile.getDocument();
        if (document != null) {
            document.accept(namespaceCollector);
        }
        return new CollectedInfo(namespaceCollector.namespaces, namespaceCollector.elements, namespaceCollector.attributes);
    }

    public static Map<String,String> findNamespaces(final XmlFile psiFile) {
        final NamespaceCollector namespaceCollector = new NamespaceCollector();
        final XmlDocument document = psiFile.getDocument();
        if (document != null) {
            document.accept(namespaceCollector);
        }
        final Set<Namespace> namespaces = namespaceCollector.namespaces;

        return convert(namespaces);
    }

    public static Map<String, String> convert(final Collection<Namespace> namespaces) {
        final Map<String, String> map = new HashMap<>();
        for (Namespace namespace : namespaces) {
            if (!map.containsKey(namespace.getPrefix())) {
                map.put(namespace.getPrefix(), namespace.getUri());
            }
        }
        return map;
    }
}
