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
package com.intellij.codeInsight.completion;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.AndFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.OrFilter;
import com.intellij.psi.filters.TextContainFilter;
import com.intellij.psi.filters.getters.HtmlAttributeValueGetter;
import com.intellij.psi.filters.getters.XmlAttributeValueGetter;
import com.intellij.psi.filters.position.XmlTokenTypeFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import org.jetbrains.annotations.NonNls;

/**
 * @author Maxim.Mossienko
 */
@SuppressWarnings({"RefusedBequest"})
public class HtmlCompletionData extends XmlCompletionData {
  private boolean myCaseInsensitive;
  private static final @NonNls String JAVASCRIPT_LANGUAGE_ID = "JavaScript";
  private static final @NonNls String STYLE_TAG = "style";
  private static final @NonNls String SCRIPT_TAG = "script";

  public HtmlCompletionData() {
    this(true);
  }

  protected HtmlCompletionData(boolean _caseInsensitive) {
    myCaseInsensitive = _caseInsensitive;
  }

  protected ElementFilter createXmlEntityCompletionFilter() {
    if (isCaseInsensitive()) {
      return new AndFilter(
        new OrFilter (
          new XmlTokenTypeFilter(XmlTokenType.XML_DATA_CHARACTERS),
          new XmlTokenTypeFilter(XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN)
        ),
        new TextContainFilter("&")
      );
    }
    
    return super.createXmlEntityCompletionFilter();
  }

  private boolean equalNames(String str,String str2) {
    if (!myCaseInsensitive) return str.equals(str2);
    return str.equalsIgnoreCase(str2);
  }

  protected boolean isCaseInsensitive() {
    return true;
  }

  protected void setCaseInsensitive(final boolean caseInsensitive) {
    myCaseInsensitive = caseInsensitive;
  }

  protected XmlAttributeValueGetter getAttributeValueGetter() {
    return new HtmlAttributeValueGetter(!isCaseInsensitive());
  }

  protected ElementFilter createTagCompletionFilter() {
    return new ElementFilter() {
      public boolean isAcceptable(Object element, PsiElement context) {
        String name = ((XmlTag)context).getName();

        if (element instanceof PsiElement && 
            ((PsiElement)element).getParent() == context) {
          return true;
        }

        if (equalNames(name, STYLE_TAG) ||
            equalNames(name,SCRIPT_TAG)) {
          return false;
        }

        if ( isStyleAttributeContext((PsiElement)element) ) return false;
        return true;
      }

      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    };
  }

  protected ElementFilter createAttributeCompletionFilter() {
    return new ElementFilter() {
      public boolean isAcceptable(Object element, PsiElement context) {
        if (isStyleAttributeContext(context)) return false;
        return true;
      }

      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    };
  }

  protected ElementFilter createAttributeValueCompletionFilter() {
    return new ElementFilter() {
      public boolean isAcceptable(Object element, PsiElement context) {
        if (isStyleAttributeContext(context)) return false;
        if ( isScriptContext((PsiElement)element) ) return false;
        return true;
      }

      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    };
  }

  private static boolean isScriptContext(PsiElement element) {
    final Language language = element.getLanguage();

    return language.getID().equals(JAVASCRIPT_LANGUAGE_ID);
  }

  private boolean isStyleAttributeContext(PsiElement position) {
    XmlAttribute parentOfType = PsiTreeUtil.getParentOfType(position, XmlAttribute.class, false);

    if (parentOfType != null) {
      String name = parentOfType.getName();
      if (myCaseInsensitive) return STYLE_TAG.equalsIgnoreCase(name);
      return STYLE_TAG.equals(name); //name.endsWith("style");
    }

    return false;
  }

  public void registerVariant(CompletionVariant variant) {
    super.registerVariant(variant);
    if (isCaseInsensitive()) variant.setCaseInsensitive(true);
  }

  public String findPrefix(PsiElement insertedElement, int offset) {
    String prefix = super.findPrefix(insertedElement, offset);

    boolean searchForEntities =
      insertedElement instanceof XmlToken &&
      ( ((XmlToken)insertedElement).getTokenType() == XmlTokenType.XML_DATA_CHARACTERS ||
        ((XmlToken)insertedElement).getTokenType() == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN
      );

    if (searchForEntities) {
      if (prefix.startsWith("&")) {
        prefix = prefix.substring(1);
      } else if (prefix.contains("&")) {
        prefix = prefix.substring(prefix.indexOf("&") + 1);
      }
    }

    return prefix;
  }

}
