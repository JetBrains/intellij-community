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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.filters.AndFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.OrFilter;
import com.intellij.psi.filters.TextContainFilter;
import com.intellij.psi.filters.getters.HtmlAttributeValueGetter;
import com.intellij.psi.filters.getters.XmlAttributeValueGetter;
import com.intellij.psi.filters.position.XmlTokenTypeFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;

import java.util.Set;

/**
 * @author Maxim.Mossienko
 */
@SuppressWarnings({"RefusedBequest"})
public class HtmlCompletionData extends XmlCompletionData {
  private static CompletionData ourStyleCompletionData;
  private boolean myCaseInsensitive;
  private static CompletionData ourScriptCompletionData;
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
        if (name == null) return true;

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

  private boolean isScriptContext(PsiElement element) {
    final Language language = element.getLanguage();

    return language.getID().equals(JAVASCRIPT_LANGUAGE_ID);
  }

  private boolean isScriptTag(XmlTag tag) {
    if (tag!=null) {
      String tagName = tag.getName();
      if (tagName == null) return false;
      if (myCaseInsensitive) return tagName.equalsIgnoreCase(SCRIPT_TAG);

      return tagName.equals(SCRIPT_TAG);
    }

    return false;
  }

  private boolean isStyleTag(XmlTag tag) {
    if (tag!=null) {
      String tagName = tag.getName();
      if (tagName == null) return false;
      if (myCaseInsensitive) return tagName.equalsIgnoreCase(STYLE_TAG);

      return tagName.equals(STYLE_TAG);
    }

    return false;
  }

  public CompletionVariant[] findVariants(final PsiElement position, final PsiFile file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<CompletionVariant[]>() {
      public CompletionVariant[] compute() {
        CompletionVariant[] variants = HtmlCompletionData.super.findVariants(position, file);

        if (ourStyleCompletionData!=null && isStyleContext(position)) {
          final CompletionVariant[] styleVariants = ourStyleCompletionData.findVariants(position, file);

          variants = ArrayUtil.mergeArrays(variants,styleVariants, CompletionVariant.class);
        }

        if (ourScriptCompletionData!=null && isScriptContext(position)) {
          final CompletionVariant[] scriptVariants = ourScriptCompletionData.findVariants(position, file);

          variants = ArrayUtil.mergeArrays(variants,scriptVariants, CompletionVariant.class);
        }
        return variants;
      }
    });
  }

  private boolean isStyleAttributeContext(PsiElement position) {
    XmlAttribute parentOfType = PsiTreeUtil.getParentOfType(position, XmlAttribute.class, false);

    if (parentOfType != null) {
      String name = parentOfType.getName();
      if (name != null) {
        if (myCaseInsensitive) return STYLE_TAG.equalsIgnoreCase(name);
        return STYLE_TAG.equals(name); //name.endsWith("style");
      }
    }

    return false;
  }
  private boolean isStyleContext(PsiElement position) {
    if (isStyleAttributeContext(position)) return true;

    return isStyleTag(PsiTreeUtil.getParentOfType(position, XmlTag.class, false));
  }

  public void addKeywordVariants(final Set<CompletionVariant> set, final PsiElement position, final PsiFile file) {
    super.addKeywordVariants(set, position, file);

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        if (ourStyleCompletionData != null && isStyleContext(position)) {
          ourStyleCompletionData.addKeywordVariants(set, position, file);
        }
        else if (ourScriptCompletionData != null && isScriptContext(position)) {
          ourScriptCompletionData.addKeywordVariants(set, position, file);
        }
      }
    });
  }

  public static void setStyleCompletionData(CompletionData cssCompletionData) {
    ourStyleCompletionData = cssCompletionData;
  }

  public void registerVariant(CompletionVariant variant) {
    super.registerVariant(variant);
    if (isCaseInsensitive()) variant.setCaseInsensitive(true);
  }

  public String findPrefix(PsiElement insertedElement, int offset) {
    XmlTag tag = PsiTreeUtil.getParentOfType(insertedElement, XmlTag.class, false);
    String prefix = null;

    if (isScriptTag(tag) &&
        ourScriptCompletionData != null &&
        !(insertedElement.getParent() instanceof XmlAttributeValue)) {
      prefix = ourScriptCompletionData.findPrefix(insertedElement, offset);
    } else if (isStyleTag(tag) && ourStyleCompletionData!=null) {
      prefix = ourStyleCompletionData.findPrefix(insertedElement, offset);
    }

    if (prefix == null) {
      prefix = super.findPrefix(insertedElement, offset);

      boolean searchForEntities =
        insertedElement instanceof XmlToken &&
        ( ((XmlToken)insertedElement).getTokenType() == XmlTokenType.XML_DATA_CHARACTERS ||
          ((XmlToken)insertedElement).getTokenType() == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN
        );
      
      if (searchForEntities && prefix != null) {
        if (prefix.startsWith("&")) {
          prefix = prefix.substring(1);
        } else if (prefix.contains("&")) {
          prefix = prefix.substring(prefix.indexOf("&") + 1);
        }
      }
    }

    return prefix;
  }

  public static void setScriptCompletionData(CompletionData scriptCompletionData) {
    ourScriptCompletionData = scriptCompletionData;
  }
}
