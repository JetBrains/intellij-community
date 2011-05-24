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

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.CreateNSDeclarationIntentionFix;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.SchemaReferencesProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ProcessingContext;
import org.intellij.lang.xpath.psi.XPath2TypeElement;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.context.XsltNamespaceContext;
import org.intellij.lang.xpath.xslt.impl.references.PrefixReference;
import org.intellij.lang.xpath.xslt.impl.references.XsltReferenceProvider;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.string;
import static com.intellij.patterns.XmlPatterns.*;

/**
 * @author yole
 */
public class XsltReferenceContributor {
  private XsltReferenceContributor() {
  }

  public static class XPath extends PsiReferenceContributor {
    public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
      registrar.registerReferenceProvider(psiElement(XPath2TypeElement.class), SchemaTypeProvider.INSTANCE);
    }
  }

  public static class XML extends PsiReferenceContributor {
    public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
      registrar.registerReferenceProvider(
        psiElement(XmlAttributeValue.class).withParent(xmlAttribute().withLocalName(string().oneOf(
          "name", "href", "mode", "elements", "exclude-result-prefixes", "extension-element-prefixes", "stylesheet-prefix"
        )).withParent(xmlTag().withNamespace(XsltSupport.XSLT_NS))),
        new XsltReferenceProvider());

      registrar.registerReferenceProvider(
        xmlAttributeValue()
          .withValue(string().matches("[^()]+"))
          .withParent(xmlAttribute("as").withParent(xmlTag().withNamespace(XsltSupport.XSLT_NS))), SchemaTypeProvider.INSTANCE);

      registrar.registerReferenceProvider(
        xmlAttributeValue()
          .withParent(xmlAttribute("as").withParent(xmlTag().withNamespace(XsltSupport.XSLT_NS)))
          .withValue(string().contains(":")), new PsiReferenceProvider() {
        @NotNull
        @Override
        public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
          return new PsiReference[]{ new NamespacePrefixReference(element) };
        }
      });
    }
  }

  static class NamespacePrefixReference extends PrefixReference implements QuickFixProvider {
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
      final XmlAttributeValue valueElement = myAttribute.getValueElement();
      if (valueElement != null) {
        QuickFixAction.registerQuickFixAction(info, new CreateNSDeclarationIntentionFix(valueElement, getCanonicalText(), (XmlFile)myAttribute.getContainingFile()) {
          @Override
          public boolean showHint(Editor editor) {
            return false;
          }
        });
      }
    }
  }

  public static class SchemaTypeReference extends SchemaReferencesProvider.TypeOrElementOrAttributeReference implements
                                                                                                             EmptyResolveMessageProvider {
    private static final Pattern NAME_PATTERN = Pattern.compile("(?:\\w+:)\\w+");

    private SchemaTypeReference(PsiElement element, TextRange range) {
      super(element, range, ReferenceType.TypeReference);
    }

    private static TextRange getTextRange(PsiElement element) {
      final Matcher matcher = NAME_PATTERN.matcher(element.getText());
      if (matcher.find()) {
        return TextRange.create(matcher.start(), matcher.end());
      } else {
        return null;
      }
    }

    @Override
    public boolean isSoft() {
      final String text = getCanonicalText();
      return super.isSoft() || isType(text, "yearMonthDuration") || isType(text, "dayTimeDuration");
    }

    private static boolean isType(String text, String name) {
      return name.equals(text) || text.endsWith(":" + name);
    }

    @Override
    public String getUnresolvedMessagePattern() {
      return "Unknown Type";
    }

    public static SchemaTypeReference create(PsiElement element) {
      final TextRange range = getTextRange(element);
      return range != null ? new SchemaTypeReference(element, range) : null;
    }
  }

  static class SchemaTypeProvider extends PsiReferenceProvider {
    static final PsiReferenceProvider INSTANCE = new SchemaTypeProvider();

    @NotNull
    @Override
    public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
      final SchemaTypeReference reference = SchemaTypeReference.create(element);
      return reference != null ? new PsiReference[] { reference } : PsiReference.EMPTY_ARRAY;
    }
  }
}
