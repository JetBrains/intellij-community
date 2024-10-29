/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.references;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.codeInspection.XmlQuickFixFactory;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.BasicAttributeValueReference;
import com.intellij.psi.impl.source.xml.SchemaPrefix;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import org.intellij.plugins.relaxNG.RelaxngBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PrefixReferenceProvider extends PsiReferenceProvider {
  private static final Logger LOG = Logger.getInstance(PrefixReferenceProvider.class);

  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    final XmlAttributeValue value = (XmlAttributeValue)element;

    final String s = value.getValue();
    final int i = s.indexOf(':');
    if (i <= 0 || s.startsWith("xml:")) {
      return PsiReference.EMPTY_ARRAY;
    }

    return new PsiReference[]{
            new PrefixReference(value, i)
    };
  }

  private static class PrefixReference extends BasicAttributeValueReference implements EmptyResolveMessageProvider, LocalQuickFixProvider {
    PrefixReference(XmlAttributeValue value, int length) {
      super(value, TextRange.from(1, length));
    }

    @Override
    public @Nullable PsiElement resolve() {
      final String prefix = getCanonicalText();
      XmlTag tag = PsiTreeUtil.getParentOfType(getElement(), XmlTag.class);
      while (tag != null) {
        if (tag.getLocalNamespaceDeclarations().containsKey(prefix)) {
          final XmlAttribute attribute = tag.getAttribute("xmlns:" + prefix, "");
          final TextRange textRange = TextRange.from("xmlns:".length(), prefix.length());
          return new SchemaPrefix(attribute, textRange, prefix);
        }
        tag = tag.getParentTag();
      }
      return null;
    }

    @Override
    public boolean isReferenceTo(@NotNull PsiElement element) {
      if (element instanceof SchemaPrefix && element.getContainingFile() == myElement.getContainingFile()) {
        final PsiElement e = resolve();
        if (e instanceof SchemaPrefix) {
          final String s = ((SchemaPrefix)e).getName();
          return s != null && s.equals(((SchemaPrefix)element).getName());
        }
      }
      return super.isReferenceTo(element);
    }

    @Override
    public @NotNull LocalQuickFix @Nullable [] getQuickFixes() {
      final PsiElement element = getElement();
      final XmlElementFactory factory = XmlElementFactory.getInstance(element.getProject());
      final String value = ((XmlAttributeValue)element).getValue();
      final String[] name = value.split(":");
      final XmlTag tag = factory.createTagFromText("<" + (name.length > 1 ? name[1] : value) + " />", XMLLanguage.INSTANCE);

      return new LocalQuickFix[] { XmlQuickFixFactory.getInstance().createNSDeclarationIntentionFix(tag, getCanonicalText(), null) };
    }

    @Override
    public boolean isSoft() {
      return false;
    }

    @Override
    public @NotNull String getUnresolvedMessagePattern() {
      //The format substitution is performed at the call site
      //noinspection UnresolvedPropertyKey
      return RelaxngBundle.message("relaxng.annotator.unresolved-namespace-prefix");
    }
  }
}