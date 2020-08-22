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

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MyPsiUtil {
    private static final Logger LOG = Logger.getInstance(MyPsiUtil.class);

    private MyPsiUtil() {
    }

    @Nullable
    public static XmlElement findContextNode(@NotNull PsiFile psiFile, @NotNull Editor editor) {
        PsiElement contextNode = psiFile.findElementAt(editor.getCaretModel().getOffset());
        while (contextNode != null && !isValidContextNode(contextNode)) {
            contextNode = contextNode.getParent();
        }

        assert contextNode == null || contextNode instanceof XmlElement;
        return (XmlElement)contextNode;
    }

    public static boolean isValidContextNode(@Nullable PsiElement contextNode) {
        return contextNode instanceof XmlTag || contextNode instanceof XmlDocument;
    }

    @NotNull
    public static PsiElement getNameElement(@NotNull XmlTag tag) {
        final PsiElement element = findNameElement(tag);
        if (element != null) {
            return element;
        }
        LOG.error("Name element not found for " + tag);
        return tag;
    }

    @Nullable
    public static PsiElement findNameElement(@NotNull XmlTag tag) {
        PsiElement[] children = tag.getChildren();
        for (PsiElement child : children) {
            if (isNameElement(child)) {
                return child;
            }
        }
        return null;
    }

    public static boolean isNameElement(@Nullable PsiElement child) {
      if (child != null) {
        if (child.getParent() instanceof XmlTag) {
          if (child instanceof XmlToken) {
            if (((XmlToken)child).getTokenType() == XmlTokenType.XML_NAME) {
              return true;
            }
          } else if (child instanceof ASTNode) {
            return ((ASTNode)child).getElementType() == XmlTokenType.XML_NAME;
          }
        }
      }
      return false;
    }

    public static String getAttributePrefix(@NotNull XmlAttribute attribute) {
        final String name = attribute.getName();
        if (name.indexOf(':') == -1) {
            return "";
        } else {
            return name.substring(0, name.indexOf(':'));
        }
    }

    public static boolean isStartTag(PsiElement contextNode) {
        if (contextNode instanceof PsiWhiteSpace) {
            PsiElement sibling = contextNode.getPrevSibling();
            while (sibling != null && !isNameElement(sibling)) {
                sibling = sibling.getPrevSibling();
            }
            return sibling != null;
        } else if (contextNode instanceof XmlToken) {
            if (((XmlToken)contextNode).getTokenType() == XmlTokenType.XML_START_TAG_START) return true;
            if (((XmlToken)contextNode).getTokenType() == XmlTokenType.XML_TAG_END) return true;
            if (((XmlToken)contextNode).getTokenType() == XmlTokenType.XML_EMPTY_ELEMENT_END) return true;
        }
        return false;
    }
    public static boolean isEndTag(PsiElement contextNode) {
        if (contextNode instanceof PsiWhiteSpace) {
            PsiElement sibling = contextNode.getPrevSibling();
            while (sibling != null && !isNameElement(sibling)) {
                sibling = sibling.getPrevSibling();
            }
            return sibling != null;
        } else if (contextNode instanceof XmlToken) {
            if (((XmlToken)contextNode).getTokenType() == XmlTokenType.XML_END_TAG_START) return true;
        }
        return false;
    }

    /**
     * This method checks if the passed element's namespace is actually declared in the document or if has an
     * implicit namespace URI which as of late, IDEA assigns to Ant files, Web descriptors and anything that has a
     * DTD defined. For XPath queries this is very inconvenient when having to enter a namespace-prefix with every
     * element-step. For XPath-Expression generation this results in more complex expressions than necessary.
     */
    public static boolean isInDeclaredNamespace(XmlTag context, String nsUri, String nsPrefix) {

        if (nsUri == null || nsUri.length() == 0 || nsPrefix != null && nsPrefix.length() > 0) {
            return true;
        }

        do {
            if (context.getLocalNamespaceDeclarations().containsValue(nsUri)) return true;
            context = (XmlTag)(context.getParent() instanceof XmlTag ? context.getParent() : null);
        } while (context != null);

        return false;
    }

    public static String checkFile(final PsiFile file) {
        final String[] error = new String[1];
        file.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitErrorElement(@NotNull PsiErrorElement element) {
                error[0] = element.getErrorDescription();
            }
        });
        if (error[0] != null) return error[0];

        final Annotator annotator = LanguageAnnotators.INSTANCE.forLanguage(file.getLanguage());
        file.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                AnnotationHolderImpl holder = new AnnotationHolderImpl(new AnnotationSession(file));
                holder.runAnnotatorWithContext(element, annotator);
                for (Annotation annotation : holder) {
                    if (annotation.getSeverity() == HighlightSeverity.ERROR) {
                        error[0] = annotation.getMessage();
                        break;
                    }
                }
                super.visitElement(element);
            }
        });
        return error[0];
    }
}
