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
package org.intellij.plugins.xpathView;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.xml.XmlAttributeDescriptor;
import org.intellij.plugins.xpathView.support.XPathSupport;
import org.intellij.plugins.xpathView.support.jaxen.PsiDocumentNavigator;
import org.intellij.plugins.xpathView.util.MyPsiUtil;
import org.intellij.plugins.xpathView.util.Namespace;
import org.jaxen.JaxenException;
import org.jaxen.XPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class XPathExpressionGenerator {
    private static final XPathSupport xpathSupport = XPathSupport.getInstance();

    private XPathExpressionGenerator() {
    }

    public static String getUniquePath(XmlElement element, XmlTag context) {
        final PathVisitor visitor = new PathVisitor(context);
        element.accept(visitor);
        return visitor.getUniquePath();
    }

    public static String getPath(XmlElement element, XmlTag context) {
        final PathVisitor visitor = new PathVisitor(context);
        element.accept(visitor);
        return visitor.getPath();
    }

    @Nullable
    public static PsiElement transformToValidShowPathNode(PsiElement contextNode) {
        PsiElement element = contextNode;
        while (element != null) {
            if (MyPsiUtil.isNameElement(element)) {
                return element.getParent();
            } else if (MyPsiUtil.isStartTag(contextNode) || MyPsiUtil.isEndTag(contextNode)) {
                return element.getParent();
            } else if (element instanceof XmlAttribute) {
                return element;
            } else if (element instanceof XmlComment) {
                return element;
            } else if (element instanceof XmlProcessingInstruction) {
                return element;
            } else if (element instanceof XmlText) {
                return element;
            }
            element = element.getParent();
        }

        return null;
    }

    private static class PathVisitor extends XmlElementVisitor {
        private final XmlTag context;
        private final Map<String, String> usedPrefixes = new HashMap<>();
        private String uniquePath;
        private String path;

        PathVisitor(XmlTag context) {
            this.context = context;
        }

        @Nullable
        private String getXPathNameStep(XmlTag tag) {
            return tag.getName();
        }

        @Override
        public void visitElement(@NotNull PsiElement element) {
            if (element instanceof XmlProcessingInstruction) {
                visitProcessingInstruction(((XmlProcessingInstruction)element));
            } else {
                super.visitElement(element);
            }
        }

        @Override
        public void visitXmlAttribute(XmlAttribute attribute) {
            uniquePath = getUniquePath(attribute);
            path = getPath(attribute);
        }

        public String getPath(XmlAttribute attribute) {
            StringBuilder result = new StringBuilder();

            XmlTag parent = attribute.getParent();

            if ((parent != null) && (parent != context)) {
                result.append(getPath(parent));
                result.append("/");
            }

            result.append("@");

            String uri = attribute.getNamespace();
            String prefix = MyPsiUtil.getAttributePrefix(attribute);

            if ((uri.length() == 0) || (prefix == null)
                    || (prefix.length() == 0)) {
                result.append(attribute.getLocalName());
            } else {
                result.append(attribute.getName());
            }

            return result.toString();
        }

        public String getUniquePath(XmlAttribute attribute) {
            StringBuilder result = new StringBuilder();

            XmlTag parent = attribute.getParent();

            if ((parent != null) && (parent != context)) {
                result.append(getUniquePath(parent));
                result.append("/");
            }

            result.append("@");

            String uri = attribute.getNamespace();
            String prefix = MyPsiUtil.getAttributePrefix(attribute);

            if ((uri.length() == 0) || (prefix == null)
                    || (prefix.length() == 0)) {
                result.append(attribute.getLocalName());
            } else {
                result.append(attribute.getName());
            }

            return result.toString();
        }


        @Override
        public void visitXmlTag(XmlTag tag) {
            uniquePath = getUniquePath(tag);
            path = getPath(tag);
        }

        private String getUniquePath(XmlTag tag) {
            XmlTag parent = tag.getParentTag();

            if (parent == null) {
                return "/" + getXPathNameStep(tag);
            }

            final StringBuilder buffer = new StringBuilder();
            if (parent != context) {
                buffer.append(getUniquePath(parent));
                buffer.append("/");
            }

            buffer.append(getXPathNameStep(tag));

            return makeUnique(buffer.toString(), tag);
        }

        @Nullable
        public String getPath(XmlTag tag) {
            if (tag == context) {
                return ".";
            }

            XmlTag parent = tag.getParentTag();

            if (parent == null) {
                return "/" + getXPathNameStep(tag);
            } else if (parent == context) {
                return getXPathNameStep(tag);
            }

            return getPath(parent) + "/" + getXPathNameStep(tag);
        }

        @Override
        public void visitXmlComment(XmlComment comment) {
            uniquePath = getUniquePath(comment);
            path = getPath(comment);
        }

        public String getPath(XmlComment comment) {
            XmlTag parent = PsiTreeUtil.getParentOfType(comment, XmlTag.class);

            return ((parent != null) && (parent != context)) ? (getPath(parent) + "/comment()")
                    : "comment()";
        }

        public String getUniquePath(XmlComment comment) {
            XmlTag parent = PsiTreeUtil.getParentOfType(comment, XmlTag.class);

            return makeUnique(((parent != null) && (parent != context)) ? (getUniquePath(parent) + "/comment()")
                    : "comment()", comment);
        }

        @Override
        public void visitXmlText(XmlText text) {
            uniquePath = getUniquePath(text);
            path = getPath(text);
        }

        public String getPath(XmlText text) {
            XmlTag parent = PsiTreeUtil.getParentOfType(text, XmlTag.class);

            return ((parent != null) && (parent != context)) ? (getPath(parent) + "/text()")
                    : "text()";
        }

        public String getUniquePath(XmlText text) {
            XmlTag parent = PsiTreeUtil.getParentOfType(text, XmlTag.class);

            return makeUnique(((parent != null) && (parent != context)) ? (getUniquePath(parent) + "/text()")
                    : "text()", text);
        }

        protected void visitProcessingInstruction(XmlProcessingInstruction processingInstruction) {
            uniquePath = getUniquePath(processingInstruction);
            path = getPath(processingInstruction);
        }

        public String getPath(XmlProcessingInstruction processingInstruction) {
            XmlTag parent = processingInstruction.getParentTag();

            return ((parent != null) && (parent != context)) ? (getPath(parent) + "/processing-instruction()")
                    : "processing-instruction()";
        }

        public String getUniquePath(XmlProcessingInstruction processingInstruction) {
            XmlTag parent = processingInstruction.getParentTag();

            final String target = PsiDocumentNavigator.getProcessingInstructionTarget(processingInstruction);
            final String s = target != null ? "'" + target + "'" : "";
            return makeUnique(((parent != null) && (parent != context)) ? (getUniquePath(parent) + "/processing-instruction(" + s + ")")
                    : "processing-instruction(" + s + ")", processingInstruction);
        }

        public String getUniquePath() {
            return uniquePath;
        }

        public String getPath() {
            return path;
        }

        String makeUnique(String uniquePath, XmlElement what) {
            final XmlFile file = (XmlFile)what.getContainingFile();
            assert file != null;
            try {
                final XPath xPath = xpathSupport.createXPath(file, uniquePath, Namespace.fromMap(usedPrefixes));
                final Object o = xPath.evaluate(file.getDocument());
                if (o instanceof List) {
                    //noinspection RawUseOfParameterizedType
                    final List list = (List)o;
                    if (list.size() > 1) {
                        if (what instanceof XmlTag) {
                            final XmlTag tag = (XmlTag)what;
                            final XmlAttribute[] attributes = tag.getAttributes();
                            if (attributes.length > 0) {
                                for (XmlAttribute attribute : attributes) {
                                    final String name = attribute.getName();
                                    final XmlAttributeDescriptor descriptor = attribute.getDescriptor();
                                    if ((attribute.getValue() != null &&
                                            (descriptor != null && descriptor.hasIdType()) ||
                                            name.equalsIgnoreCase("id") ||
                                            name.equalsIgnoreCase("name"))) {
                                        final StringBuilder buffer = new StringBuilder(uniquePath);
                                        buffer.append("[@");
                                        buffer.append(name);
                                        buffer.append("='");
                                        buffer.append(attribute.getValue());
                                        buffer.append("']");
                                        return buffer.toString();
                                    }
                                }
                            }
                        }

                        int i = 1;
                        for (Object o1 : list) {
                            if (o1 == what) {
                                return uniquePath + "[" + i + "]";
                            } else {
                                i++;
                            }
                        }
                        assert false : "Expression " + uniquePath + " didn't find input element " + what;
                    }
                } else {
                    assert false : "Unknown return value: " + o;
                }
            } catch (JaxenException e) {
                Logger.getInstance("XPathExpressionGenerator").error(e);
            }
            return uniquePath;
        }
    }
}
