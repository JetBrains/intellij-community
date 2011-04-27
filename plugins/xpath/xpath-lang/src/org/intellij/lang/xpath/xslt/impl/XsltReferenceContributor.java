/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.intellij.lang.xpath.xslt.impl;

import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.CreateNSDeclarationIntentionFix;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ProcessingContext;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.context.XsltNamespaceContext;
import org.intellij.lang.xpath.xslt.impl.references.PrefixReference;
import org.intellij.lang.xpath.xslt.impl.references.XsltReferenceProvider;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.StandardPatterns.string;
import static com.intellij.patterns.XmlPatterns.xmlAttribute;
import static com.intellij.patterns.XmlPatterns.xmlTag;

/**
 * @author yole
 */
public class XsltReferenceContributor extends PsiReferenceContributor {
  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(XmlAttributeValue.class).withParent(xmlAttribute().withLocalName(string().oneOf(
              "name", "href", "mode", "elements", "exclude-result-prefixes", "extension-element-prefixes", "stylesheet-prefix"
            )).withParent(xmlTag().withNamespace(XsltSupport.XSLT_NS))),
            new XsltReferenceProvider(registrar.getProject()));

// TODO: 1. SchemaReferencesProvider doesn't know about "as" attribute / 2. what to do with non-schema types (xs:yearMonthDuretion, xs:dayTimeDuration)?
//    registrar.registerReferenceProvider(
//            XmlPatterns.xmlAttributeValue().withParent(xmlAttribute("as").withParent(xmlTag().withNamespace(XsltSupport.XSLT_NS))),
//            new SchemaReferencesProvider());

    registrar.registerReferenceProvider(
            XmlPatterns.xmlAttributeValue().withParent(xmlAttribute("as").withParent(xmlTag().withNamespace(XsltSupport.XSLT_NS))),
            new PsiReferenceProvider() {
              @NotNull
              @Override
              public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                if (element.textContains(':')) {
                  return new PsiReference[]{ new NamespacePrefixReference(element) };
                }
                return PsiReference.EMPTY_ARRAY;
              }
            });
  }

  private static class NamespacePrefixReference extends PrefixReference implements QuickFixProvider {
    public NamespacePrefixReference(PsiElement element) {
      super((XmlAttribute)element.getParent());
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      return XsltNamespaceContext.getPrefixes(myAttribute).toArray();
    }

    @Override
    public void registerQuickfix(HighlightInfo info, PsiReference reference) {
      QuickFixAction.registerQuickFixAction(info, new CreateNSDeclarationIntentionFix(myAttribute.getValueElement(), getCanonicalText(), (XmlFile)myAttribute.getContainingFile()) {
        @Override
        public boolean showHint(Editor editor) {
          return false;
        }
      });
    }
  }
}
