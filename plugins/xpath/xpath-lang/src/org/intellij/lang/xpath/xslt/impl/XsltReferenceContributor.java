// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.xpath.xslt.impl;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.impl.analysis.CreateNSDeclarationIntentionFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.XmlTagPattern;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.TypeOrElementOrAttributeReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ProcessingContext;
import org.intellij.lang.xpath.psi.XPath2TypeElement;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.context.XsltNamespaceContext;
import org.intellij.lang.xpath.xslt.impl.references.PrefixReference;
import org.intellij.lang.xpath.xslt.impl.references.XsltReferenceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.string;
import static com.intellij.patterns.XmlPatterns.xmlAttributeValue;
import static com.intellij.patterns.XmlPatterns.xmlTag;

/**
 * @author yole
 */
public final class XsltReferenceContributor {
  private XsltReferenceContributor() {
  }

  public static class XPath extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
      registrar.registerReferenceProvider(psiElement(XPath2TypeElement.class), SchemaTypeProvider.INSTANCE);
    }
  }

  public static class XML extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
      final XmlTagPattern xsltTag = xmlTag().withNamespace(XsltSupport.XSLT_NS);
      registrar.registerReferenceProvider(
        xmlAttributeValue("name", "href", "mode", "elements", "exclude-result-prefixes", "extension-element-prefixes", "stylesheet-prefix")
          .withSuperParent(2, xsltTag),
        new XsltReferenceProvider());

      registrar.registerReferenceProvider(
        xmlAttributeValue("as")
          .withValue(string().matches("[^()]+"))
          .withSuperParent(2, xsltTag), SchemaTypeProvider.INSTANCE);

      registrar.registerReferenceProvider(
        xmlAttributeValue("as")
          .withSuperParent(2, xsltTag)
          .withValue(string().contains(":")), new PsiReferenceProvider() {
        @Override
        public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
          return new PsiReference[]{ new NamespacePrefixReference(element) };
        }
      });
    }
  }

  static class NamespacePrefixReference extends PrefixReference implements LocalQuickFixProvider {
    NamespacePrefixReference(PsiElement element) {
      super((XmlAttribute)element.getParent());
    }

    @Override
    public Object @NotNull [] getVariants() {
      return XsltNamespaceContext.getPrefixes(myAttribute).toArray();
    }

    @Override
    public LocalQuickFix @Nullable [] getQuickFixes() {
      final XmlAttributeValue valueElement = myAttribute.getValueElement();
      if (valueElement != null) {
        return new LocalQuickFix[] {
          new CreateNSDeclarationIntentionFix(valueElement, getCanonicalText()) {
            @Override
            public boolean showHint(@NotNull Editor editor) {
              return false;
            }
          }
        };
      }
      return LocalQuickFix.EMPTY_ARRAY;
    }
  }

  public static final class SchemaTypeReference extends TypeOrElementOrAttributeReference implements
                                                                                                             EmptyResolveMessageProvider {
    private static final Pattern NAME_PATTERN = Pattern.compile("(?:[\\w-]+:)[\\w-]+");

    private SchemaTypeReference(PsiElement element, TextRange range) {
      super(element, range, ReferenceType.TypeReference);
    }

    @Nullable
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

    @NotNull
    @Override
    public String getUnresolvedMessagePattern() {
      return "Unknown Type";
    }

    @Nullable
    public static SchemaTypeReference create(PsiElement element) {
      final TextRange range = getTextRange(element);
      return range != null ? new SchemaTypeReference(element, range) : null;
    }
  }

  static class SchemaTypeProvider extends PsiReferenceProvider {
    static final PsiReferenceProvider INSTANCE = new SchemaTypeProvider();

    @Override
    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
      final SchemaTypeReference reference = SchemaTypeReference.create(element);
      return reference != null ? new PsiReference[] { reference } : PsiReference.EMPTY_ARRAY;
    }
  }
}
