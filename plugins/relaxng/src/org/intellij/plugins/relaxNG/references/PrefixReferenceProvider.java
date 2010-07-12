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
import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.CreateNSDeclarationIntentionFix;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProviderBase;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.BasicAttributeValueReference;
import com.intellij.psi.impl.source.xml.SchemaPrefix;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 24.07.2007
*/
public class PrefixReferenceProvider extends PsiReferenceProviderBase {
  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
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

  private static class PrefixReference extends BasicAttributeValueReference implements EmptyResolveMessageProvider, QuickFixProvider<PrefixReference> {
    public PrefixReference(XmlAttributeValue value, int length) {
      super(value, TextRange.from(1, length));
    }

    @Nullable
    public PsiElement resolve() {
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
    public boolean isReferenceTo(PsiElement element) {
      if (element instanceof SchemaPrefix && element.getContainingFile() == myElement.getContainingFile()) {
        final PsiElement e = resolve();
        if (e instanceof SchemaPrefix) {
          final String s = ((SchemaPrefix)e).getName();
          return s != null && s.equals(((SchemaPrefix)element).getName());
        }
      }
      return super.isReferenceTo(element);
    }

    public void registerQuickfix(HighlightInfo info, PrefixReference reference) {
      try {
        final PsiElement element = reference.getElement();
        final XmlElementFactory factory = XmlElementFactory.getInstance(element.getProject());
        final String value = ((XmlAttributeValue)element).getValue();
        final String[] name = value.split(":");
        final XmlTag tag = factory.createTagFromText("<" + (name.length > 1 ? name[1] : value) + " />", XMLLanguage.INSTANCE);

        QuickFixAction.registerQuickFixAction(info,
              new CreateNSDeclarationIntentionFix(tag, reference.getCanonicalText()));
      } catch (Throwable e) {
        e.printStackTrace();
      }
    }

    @NotNull
    public Object[] getVariants() {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    public boolean isSoft() {
      return false;
    }

    public String getUnresolvedMessagePattern() {
      return "Undefined namespace prefix ''{0}''";
    }
  }
}