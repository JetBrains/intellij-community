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
package org.intellij.lang.xpath.xslt.context;

import com.intellij.codeInsight.daemon.impl.analysis.CreateNSDeclarationIntentionFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.impl.source.xml.SchemaPrefix;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.xpath.context.NamespaceContext;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.impl.XsltChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class XsltNamespaceContext implements NamespaceContext {
    public static final XsltNamespaceContext NAMESPACE_CONTEXT = new XsltNamespaceContext();

    @Override
    public String getNamespaceURI(String prefix, XmlElement context) {
        return getNamespaceUriStatic(prefix, context);
    }

    @Nullable
    public static String getNamespaceUriStatic(String prefix, XmlElement context) {
        final XmlTag tag = PsiTreeUtil.getParentOfType(context, XmlTag.class);
        return tag != null ? tag.getNamespaceByPrefix(prefix) : null;
    }

    @Override
    @Nullable
    public String getPrefixForURI(String uri, XmlElement context) {
        final XmlTag tag = PsiTreeUtil.getParentOfType(context, XmlTag.class);
        return tag != null ? tag.getPrefixByNamespace(uri) : null;
    }

    @Override
    @NotNull
    public Collection<String> getKnownPrefixes(XmlElement context) {
        return getPrefixes(context);
    }

    public static Collection<String> getPrefixes(XmlElement context) {
        final XmlTag tag = PsiTreeUtil.getParentOfType(context, XmlTag.class);
        if (tag != null) {
            final HashSet<String> allPrefixes = new HashSet<>();
            final String[] uris = tag.knownNamespaces();
            for (String uri : uris) {
                for (XmlTag p = tag; p != null; p = p.getParentTag()) {
                    final Set<Map.Entry<String,String>> localPrefixes = p.getLocalNamespaceDeclarations().entrySet();
                    for (Map.Entry<String,String> entry : localPrefixes) {
                        final String prefix = entry.getKey();
                        if (!prefix.isEmpty() && entry.getValue().equals(uri)) {
                            if (!allPrefixes.contains(prefix)) {
                                allPrefixes.add(prefix);
                            }
                        }
                    }
                }
            }
            return allPrefixes;
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    @Nullable
    public PsiElement resolve(String prefix, XmlElement context) {
        return resolvePrefix(prefix, context);
    }

    @Nullable
    public static PsiElement resolvePrefix(final String prefix, XmlElement context) {
        final String name = "xmlns:" + prefix;

        XmlTag parent = PsiTreeUtil.getParentOfType(context, XmlTag.class);
        while (parent != null) {
            final XmlAttribute attribute = parent.getAttribute(name, null);
            if (attribute != null) {
              final TextRange textRange = TextRange.from("xmlns:".length(), prefix.length());
              return new SchemaPrefix(attribute, textRange, prefix) {
                @Override
                public boolean equals(Object obj) {
                  if (obj instanceof SchemaPrefix) {
                    final SchemaPrefix p = (SchemaPrefix)obj;
                    return prefix.equals(p.getName()) && p.getParent() == attribute;
                  }
                  return super.equals(obj);
                }
              };
            }
            parent = PsiTreeUtil.getParentOfType(parent, XmlTag.class);
        }
        return null;
    }

    @Override
    public IntentionAction[] getUnresolvedNamespaceFixes(PsiReference reference, String localName) {
        return getUnresolvedNamespaceFixesStatic(reference, localName);
    }

    @Override
    public String getDefaultNamespace(XmlElement context) {
      if (context != null) {
        if (XsltSupport.getXsltLanguageLevel(context.getContainingFile()) == XsltChecker.LanguageLevel.V2) {
          context = PsiTreeUtil.getParentOfType(context, XmlTag.class, false);

          if (context != null) {
            do {
              final XmlTag tag = (XmlTag)context;
              String uri;
              if ((uri = tag.getAttributeValue("xpath-default-namespace", null)) != null ||
                  (uri = tag.getAttributeValue("xpath-default-namespace", XsltSupport.XSLT_NS)) != null) {
                return !uri.isEmpty() ? uri : null;
              }
              context = PsiTreeUtil.getParentOfType(context, XmlTag.class, true);
            }
            while (context != null);
          }
        }
      }

      return null;
    }

    public static IntentionAction[] getUnresolvedNamespaceFixesStatic(PsiReference reference, String localName) {
        final XmlElementFactory factory = XmlElementFactory.getInstance(reference.getElement().getProject());
        final XmlTag tag = factory.createTagFromText("<" + reference.getCanonicalText() + ":" + localName + " />", XMLLanguage.INSTANCE);

        final XmlFile xmlFile = PsiTreeUtil.getContextOfType(reference.getElement(), XmlFile.class, true);
        return new IntentionAction[]{
                new MyCreateNSDeclarationAction(tag, reference.getCanonicalText(), xmlFile)
        };
    }

    static class MyCreateNSDeclarationAction extends CreateNSDeclarationIntentionFix {
        private final XmlFile myXmlFile;

        // TODO: verify API
        public MyCreateNSDeclarationAction(XmlElement xmlElement, String prefix, XmlFile xmlFile) {
            super(xmlElement, prefix);
            myXmlFile = xmlFile;
        }

        @Override
        public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
            super.invoke(project, editor, myXmlFile);
        }

        @Override
        public boolean showHint(@NotNull Editor editor) {
            return false; // doesn't work properly yet
        }

        @Override
        public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
            return super.isAvailable(project, editor, myXmlFile);
        }
    }
}
