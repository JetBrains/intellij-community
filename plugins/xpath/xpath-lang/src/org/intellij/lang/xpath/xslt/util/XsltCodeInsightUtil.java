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

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiElementFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.xpath.psi.*;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.XsltElement;
import org.intellij.lang.xpath.xslt.psi.XsltElementFactory;
import org.intellij.lang.xpath.xslt.psi.XsltTemplate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class XsltCodeInsightUtil {
    public static final PsiElementFilter XSLT_PARAM_FILTER = new PsiElementFilter() {
        public boolean isAccepted(PsiElement element) {
            return element instanceof XmlTag && XsltSupport.isParam((XmlTag)element);
        }
    };
    public static final Comparator<PsiElement> POSITION_COMPARATOR = (o1, o2) -> o1.getTextOffset() - o2.getTextOffset();

    private XsltCodeInsightUtil() {
    }

    @Nullable
    public static XmlTag getTemplateTag(@NotNull PsiElement location, boolean isExpression, boolean requireName) {
        PsiElement p = isExpression ? location.getContainingFile().getContext() : location;
        while ((p = PsiTreeUtil.getParentOfType(p, XmlTag.class)) != null) {
            final XmlTag _p = ((XmlTag)p);
            if (XsltSupport.isTemplate(_p, requireName)) return _p;
        }
        return null;
    }

    @Nullable
    public static XmlTag getTemplateTag(@NotNull PsiElement location, boolean isExpression) {
        return getTemplateTag(location, isExpression, false);
    }

    @Nullable
    public static XsltTemplate getTemplate(@NotNull PsiElement location, boolean isExpression) {
        final XmlTag templateTag = getTemplateTag(location, isExpression);
        return templateTag != null ? XsltElementFactory.getInstance().wrapElement(templateTag, XsltTemplate.class) : null;
    }

    @Nullable
    public static PsiElement findFirstRealTagChild(@NotNull XmlTag xmlTag) {
        final PsiElement[] child = new PsiElement[1];
        xmlTag.processElements(new PsiElementProcessor() {
            public boolean execute(@NotNull PsiElement element) {
                if (element instanceof XmlToken) {
                    if (((XmlToken)element).getTokenType() == XmlTokenType.XML_TAG_END) {
                        child[0] = element.getNextSibling();
                        return false;
                    }
                }
                return true;
            }
        }, xmlTag);
        return child[0];
    }

    @Nullable
    public static XPathExpression getXPathExpression(XsltElement xsltElement, String attributeName) {
        final XmlAttribute attribute = xsltElement.getTag().getAttribute(attributeName, null);
        if (attribute != null) {
            final PsiFile[] files = XsltSupport.getFiles(attribute);
            if (files.length > 0) {
                assert files.length == 1 : "Unexpected number of XPathFiles in @" + attributeName + ": " + Arrays.toString(files);
                return PsiTreeUtil.getChildOfType(files[0], XPathExpression.class);
            }
        }
        return null;
    }

    public static boolean areExpressionsEquivalent(XPathExpression x1, XPathExpression x2) {
        if (x1.getType() != x2.getType()) return false;
        // another hidden and hard-to-find goodie from the dark non-OpenAPI world
        return PsiEquivalenceUtil.areElementsEquivalent(x1, x2);
    }

    @Nullable
    public static XmlTag findLastParam(XmlTag templateTag) {
        final ArrayList<XmlTag> list = new ArrayList<>();
        final PsiElementProcessor.CollectFilteredElements<XmlTag> processor =
          new PsiElementProcessor.CollectFilteredElements<>(XSLT_PARAM_FILTER, list);
        templateTag.processElements(processor, templateTag);

        return list.size() > 0 ? list.get(list.size() - 1) : null;
    }

  @NotNull
    public static TextRange getRangeInsideHostingFile(XPathElement expr) {
        final PsiLanguageInjectionHost host = PsiTreeUtil.getContextOfType(expr, PsiLanguageInjectionHost.class, true);
        assert host != null;
        final List<Pair<PsiElement,TextRange>> psi = InjectedLanguageManager.getInstance(host.getProject()).getInjectedPsiFiles(host);
        assert psi != null;
        for (Pair<PsiElement, TextRange> pair : psi) {
            if (PsiTreeUtil.isAncestor(pair.first, expr, false)) {
                return expr.getTextRange().shiftRight(pair.second.getStartOffset() + host.getTextRange().getStartOffset());
            }
        }
        assert false;
        return null;
    }

    @NotNull
    public static TextRange getRangeInsideHost(XPathElement expr) {
        final PsiLanguageInjectionHost host = PsiTreeUtil.getContextOfType(expr, PsiLanguageInjectionHost.class, true);
        assert host != null;
        final List<Pair<PsiElement,TextRange>> psi = InjectedLanguageManager.getInstance(host.getProject()).getInjectedPsiFiles(host);
        assert psi != null;
        for (Pair<PsiElement, TextRange> pair : psi) {
            if (PsiTreeUtil.isAncestor(pair.first, expr, false)) {
                return pair.second;
            }
        }
        assert false;
        return null;
    }

    public static XmlTag findLastWithParam(XmlTag templateTag) {
        final XmlTag[] lastParam = new XmlTag[1];
        templateTag.processElements(new PsiElementProcessor() {
            public boolean execute(@NotNull PsiElement element) {
                if (element instanceof XmlTag) {
                    if ("with-param".equals(((XmlTag)element).getLocalName())) {
                        lastParam[0] = (XmlTag)element;
                    } else {
                        return false;
                    }
                }
                return true;
            }
        }, templateTag);
        return lastParam[0];
    }

    public static XmlTag findVariableInsertionPoint(final XmlTag currentUsageTag, PsiElement usageBlock, final String referenceName, XmlTag... moreUsages) {
      // sort tags by document order
      final Set<XmlTag> usages = new TreeSet<>(POSITION_COMPARATOR);
      usages.add(currentUsageTag);
      ContainerUtil.addAll(usages, moreUsages);

      // collect all other possible unresolved references with the same name in the current template
      usageBlock.accept(new PsiRecursiveElementVisitor() {
        public void visitElement(PsiElement element) {
          if (element instanceof XPathVariableReference) {
            visitXPathVariableReference(((XPathVariableReference)element));
          }
          else {
            super.visitElement(element);
          }
        }

        private void visitXPathVariableReference(XPathVariableReference reference) {
          if (referenceName.equals(reference.getReferencedName())) {
            if (reference.resolve() == null) {
              usages.add(PsiTreeUtil.getContextOfType(reference, XmlTag.class, true));
            }
          }
        }

        public void visitXmlAttribute(XmlAttribute attribute) {
          if (XsltSupport.isXPathAttribute(attribute)) {
            final PsiFile[] xpathFiles = XsltSupport.getFiles(attribute);
            for (PsiFile xpathFile : xpathFiles) {
              xpathFile.accept(this);
            }
          }
        }
      });

      final Iterator<XmlTag> it = usages.iterator();
      final XmlTag firstUsage = it.next();

      // find broadest scope to create the variable in
      XmlTag tag = firstUsage;
      while (it.hasNext()) {
        XmlTag xmlTag = it.next();
        final PsiElement t = PsiTreeUtil.findCommonParent(tag, xmlTag);
        if (t instanceof XmlTag) {
          tag = (XmlTag)t;
        }
        else {
          break;
        }
      }

      // find the actual tag to create the variable before
      final XmlTag[] subTags = tag.getSubTags();
      for (XmlTag xmlTag : subTags) {
        if (xmlTag.getTextOffset() > firstUsage.getTextOffset()) break;
        tag = xmlTag;
      }

      final XmlTag parentTag = tag.getParentTag();
      if (parentTag == null) return tag;

      final String parentName = parentTag.getLocalName();
      if ("apply-templates".equals(parentName) || "call-template".equals(parentName)
          || "when".equals(parentName) || "choose".equals(parentName)) {
        if ("when".equals(parentName)) tag = tag.getParentTag();
        assert tag != null;
        tag = tag.getParentTag();
      }
      assert tag != null;
      return tag;
    }

    @Nullable
    public static PsiElement getUsageBlock(XPathExpression reference) {
        final XmlTag template = getTemplateTag(reference, true);
        final XmlTag tag = PsiTreeUtil.getContextOfType(reference, XmlTag.class, true);
        assert tag != null;
        return template != null ? template.getNavigationElement() : tag.getParentTag();
    }

    @NotNull
    public static XmlDocument getDocument(@NotNull XmlElement element) {
        final XmlDocument document = PsiTreeUtil.getParentOfType(element, XmlDocument.class, false);
        assert document != null;
        return document;
    }

    public static XmlDocument getDocument(@NotNull XsltElement element) {
        return getDocument(element.getTag());
    }

  @Nullable
  public static XPathType getDeclaredType(XmlTag element) {
    final XmlAttribute typeAttr = element.getAttribute("as");
    final XPathType returnType;
    if (typeAttr != null) {
      final String value = typeAttr.getValue();
      returnType = value != null ? XPath2Type.fromName(QNameUtil.createQName(value, element)) : null;
      return returnType != null ? returnType : XPathType.UNKNOWN;
    }
    return null;
  }
}
