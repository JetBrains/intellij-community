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
package org.intellij.lang.xpath.xslt.util;

import org.intellij.lang.xpath.xslt.context.XsltNamespaceContext;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.namespace.QName;

public class QNameUtil {
    public static final QName NONE = null;
    public static final QName UNRESOLVED = new ExtendedQName("null");
    public static final QName ANY = new ExtendedQName("*");

    private QNameUtil() {
    }

    public static boolean equal(QName q1, QName q2) {
        if (q1 == UNRESOLVED || q2 == UNRESOLVED) return false;
        if (q1 == ANY || q2 == ANY) return true;
        if (Comparing.equal(q1, q2)) return true;

        if (q1 instanceof ExtendedQName) {
            return q1.getNamespaceURI().equals(q2.getNamespaceURI());
        } else if (q2 instanceof ExtendedQName) {
            return q2.getNamespaceURI().equals(q1.getNamespaceURI());
        }
        return false;
    }

    public static QName createAnyLocalName(String namespace) {
        return new ExtendedQName(namespace);
    }

    @SuppressWarnings({"SimplifiableIfStatement"})
    private static boolean isNamespaceDeclared(@Nullable XmlTag tag, String namespace) {
        if (tag == null) return false;
        if (tag.getLocalNamespaceDeclarations().containsValue(namespace)) return true;
        return isNamespaceDeclared(tag.getParentTag(), namespace);
    }

    public static QName createQName(@NotNull String qname, @NotNull PsiElement context) {
        final String[] strings = qname.split(":", 2);
        if (strings.length == 1) {
            return new QName(null, qname);
        }
        final XmlElement ctx = PsiTreeUtil.getParentOfType(context, XmlElement.class, false);
        if (ctx == null) return UNRESOLVED;
        final String uri = XsltNamespaceContext.getNamespaceUriStatic(strings[0], ctx);
        if (uri == null) return UNRESOLVED;

        return new QName(uri, strings[1], strings[0]);
    }

    public static QName createQName(@NotNull XmlAttribute attribute) {
        final String name = attribute.getName();

        if (name.indexOf(':') != -1) {
            return new QName(attribute.getNamespace(), attribute.getLocalName());
        } else {
            return new QName(null, attribute.getLocalName());
        }
    }

    public static QName createQName(@NotNull XmlTag tag) {
        if (isNamespaceDeclared(tag, tag.getNamespace())) {
            return new QName(tag.getNamespace(), tag.getLocalName(), tag.getNamespacePrefix());
        } else {
            return new QName(null, tag.getLocalName());
        }
    }

    static final class ExtendedQName extends QName {
        ExtendedQName(String namespace) {
            super(namespace, "*");
        }
    }
}