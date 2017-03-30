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

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.*;
import com.intellij.xml.XmlAttributeDescriptor;
import org.intellij.plugins.xpathView.util.MyPsiUtil;
import org.jaxen.DefaultNavigator;
import org.jaxen.FunctionCallException;
import org.jaxen.UnsupportedAxisException;
import org.jaxen.XPath;
import org.jaxen.saxpath.SAXPathException;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Iterator;

/**
 * <p>Adapter class for IDEA's PSI-tree to Jaxen.</p>
 * Not all of the required functionality is implemented yet. See the TODO comments...
 */
public class PsiDocumentNavigator extends DefaultNavigator {

    private static final Logger LOG = Logger.getInstance("org.intellij.plugins.xpathView.support.jaxen.PsiDocumentNavigator");
    private final XmlFile file;

    public PsiDocumentNavigator(XmlFile file) {
        this.file = file;
    }

    public Iterator getChildAxisIterator(Object contextNode) throws UnsupportedAxisException {
        if (!(contextNode instanceof XmlElement)) {
            return Collections.emptyList().iterator();
        }
        return new PsiChildAxisIterator(contextNode);
    }


    public Iterator getParentAxisIterator(Object contextNode) {
        if (!(contextNode instanceof XmlElement)) {
            return Collections.emptyList().iterator();
        }

        return new NodeIterator((XmlElement)contextNode) {
            protected PsiElement getFirstNode(PsiElement n) {
                while (n != null) {
                    n = n.getParent();
                    if (n instanceof XmlTag) {
                        return n;
                    }
                }
                return null;
            }

            protected PsiElement getNextNode(PsiElement n) {
                return null;
            }
        };
    }


    public Iterator getNamespaceAxisIterator(Object contextNode) throws UnsupportedAxisException {
        return super.getNamespaceAxisIterator(contextNode);
    }

    public Object getDocumentNode(Object contextNode) {
        if (contextNode instanceof XmlDocument) {
            return contextNode;
        }

        while (contextNode instanceof PsiElement) {
            if (contextNode instanceof XmlDocument) {
                return contextNode;
            }
            contextNode = ((PsiElement)contextNode).getParent();
        }

        return null;
    }

    public String translateNamespacePrefixToUri(String prefix, Object element) {
        if (isElement(element)) {
            return ((XmlTag)element).getNamespaceByPrefix(prefix);
        }
        return super.translateNamespacePrefixToUri(prefix, element);
    }

    public String getProcessingInstructionTarget(Object obj) {
        LOG.assertTrue(obj instanceof XmlProcessingInstruction);

        XmlProcessingInstruction pi = (XmlProcessingInstruction)obj;
        return getProcessingInstructionTarget(pi);
    }

    public static String getProcessingInstructionTarget(XmlProcessingInstruction pi) {
        final PsiElement[] children = pi.getChildren();
        LOG.assertTrue(children[1] instanceof XmlToken && ((XmlToken)children[1]).getTokenType() == XmlTokenType.XML_NAME, "Unknown PI structure");

        String text = children[1].getText();
        int i;
        for (i=0; i<text.length() && text.charAt(i) == ' ';) i++; // skip
        final int pos = text.indexOf(' ', i);
        if (pos != -1) {
            text = text.substring(i, pos);
        } else {
            text = text.substring(i);
        }

        return text;
    }

    @NotNull
    public String getProcessingInstructionData(Object obj) {
        LOG.assertTrue(obj instanceof XmlProcessingInstruction);

        XmlProcessingInstruction pi = (XmlProcessingInstruction)obj;
        int targetLength = getProcessingInstructionTarget(obj).length();
        int piLength= pi.getText().length();
        return pi.getText().substring(2 + targetLength, piLength - 2).trim();
    }

    public Object getParentNode(Object contextNode) throws UnsupportedAxisException {
        return ((PsiElement)contextNode).getParent();
    }

    public Object getDocument(String url) throws FunctionCallException {
        final VirtualFile virtualFile = VfsUtilCore.findRelativeFile(url, file.getVirtualFile());
        if (virtualFile != null) {
            final PsiFile file = this.file.getManager().findFile(virtualFile);
            if (file instanceof XmlFile) {
                return ((XmlFile)file).getDocument();
            }
        }
        return null;
    }

    public Iterator getAttributeAxisIterator(Object contextNode) {
        if (isElement(contextNode)) {
            return new AttributeIterator((XmlElement)contextNode);
        } else {
            return Collections.emptyList().iterator();
        }
    }

    public String getElementNamespaceUri(Object element) {
        LOG.assertTrue(element instanceof XmlTag);

        final XmlTag context = (XmlTag)element;
        final String namespaceUri = context.getNamespace();
        if (!MyPsiUtil.isInDeclaredNamespace(context, namespaceUri, context.getNamespacePrefix())) {
          return "";
        }
        return namespaceUri;
    }

    public String getElementName(Object element) {
        LOG.assertTrue(element instanceof XmlTag);
        return ((XmlTag)element).getLocalName();
    }

    public String getElementQName(Object element) {
        LOG.assertTrue(element instanceof XmlTag);
        return ((XmlTag)element).getName();
    }

    public String getAttributeNamespaceUri(Object attr) {
        LOG.assertTrue(attr instanceof XmlAttribute);

        final XmlAttribute attribute = ((XmlAttribute)attr);
        final String name = attribute.getName();
        if (name.indexOf(':') == -1) return "";

        final String uri = attribute.getNamespace();
        if (!MyPsiUtil.isInDeclaredNamespace(attribute.getParent(), uri, MyPsiUtil.getAttributePrefix(attribute))) {
            LOG.info("getElementNamespaceUri: not returning implicit attribute-namespace uri: " + uri);
            return "";
        }
        return uri;
    }

    public String getAttributeName(Object attr) {
        LOG.assertTrue(attr instanceof XmlAttribute);
        return ((XmlAttribute)attr).getLocalName();
    }

    public String getAttributeQName(Object attr) {
        LOG.assertTrue(attr instanceof XmlAttribute);
        return ((XmlAttribute)attr).getName();
    }

    public boolean isDocument(Object object) {
        return object instanceof XmlDocument;
    }

    public boolean isElement(Object object) {
        return object instanceof XmlTag && isSupportedElement((XmlTag)object);
    }

    private static boolean isSupportedElement(XmlTag object) {
        // optimization: all tags from XML language are supported, but some from other languages (JSP, see IDEADEV-37939) are not
        return object.getLanguage() == XMLLanguage.INSTANCE || MyPsiUtil.findNameElement(object) != null;
    }

    public boolean isAttribute(Object object) {
        return object instanceof XmlAttribute;
    }

    public boolean isNamespace(Object object) {
        // TODO: implement when namespace axis is supported
        return false;
    }

    public boolean isComment(Object object) {
        return object instanceof XmlComment;
    }

    public boolean isText(Object object) {
        return object instanceof PsiWhiteSpace ? ((PsiWhiteSpace)object).getParent() instanceof XmlText : object instanceof XmlText;
    }

    public boolean isProcessingInstruction(Object object) {
        return object instanceof XmlProcessingInstruction;
    }

    @NotNull
    public String getCommentStringValue(Object comment) {
        LOG.assertTrue(comment instanceof XmlComment);

        PsiElement c = (PsiElement)comment;
        final PsiElement[] children = c.getChildren();
        for (PsiElement child : children) {
            if (child instanceof XmlToken && ((XmlToken)child).getTokenType() == XmlTokenType.XML_COMMENT_CHARACTERS) {
                return child.getText();
            }
        }
        return "";
    }

    @NotNull
    public String getElementStringValue(Object element) {
        LOG.assertTrue(element instanceof XmlTag);

        final TextCollector collector = new TextCollector();
        ((XmlTag)element).accept(collector);
        return collector.getText();
    }

    @NotNull
    public String getAttributeStringValue(Object attr) {
        LOG.assertTrue(attr instanceof XmlAttribute);
        return StringUtil.notNullize(((XmlAttribute)attr).getValue());
    }

    public String getNamespaceStringValue(Object ns) {
        // TODO: implement when namespace axis is supported
        return null;
    }

    public String getNamespacePrefix(Object ns) {
        // TODO: implement when namespace axis is supported
        return null;
    }

    @NotNull
    public String getTextStringValue(Object txt) {
        
        if (txt instanceof XmlText) {
          return ((XmlText)txt).getValue();
        }
        return txt instanceof PsiElement ? ((PsiElement)txt).getText() : txt.toString();
    }

    public XPath parseXPath(String xpath) throws SAXPathException {
        return new PsiXPath(file, xpath);
    }

    public Object getElementById(Object object, final String elementId) {
      final XmlTag rootTag = ((XmlFile)((XmlElement)object).getContainingFile()).getRootTag();
      if (rootTag == null) {
        return null;
      }

      final Ref<XmlTag> ref = new Ref<>();
      rootTag.accept(new XmlRecursiveElementVisitor() {
        @Override
        public void visitElement(PsiElement element) {
          if (ref.get() == null) {
            super.visitElement(element);
          }
        }

        @Override
        public void visitXmlAttribute(XmlAttribute attribute) {
          final XmlAttributeDescriptor descriptor = attribute.getDescriptor();
          final String value = attribute.getValue();
          if ((value != null &&
               (descriptor != null && descriptor.hasIdType()))) {
            if (elementId.equals(value)) {
              ref.set(attribute.getParent());
            }
          }
        }
      });
      return ref.get();
    }

    static class TextCollector extends XmlRecursiveElementVisitor {
        private final StringBuffer builder = new StringBuffer();

        @Override
        public void visitXmlText(XmlText text) {
            builder.append(text.getValue());
        }

        public String getText() {
            return builder.toString();
        }
    }
}
