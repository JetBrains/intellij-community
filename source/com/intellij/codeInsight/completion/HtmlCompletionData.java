package com.intellij.codeInsight.completion;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.util.ArrayUtil;
import com.intellij.lang.Language;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Oct 13, 2004
 * Time: 6:50:33 PM
 * To change this template use File | Settings | File Templates.
 */
public class HtmlCompletionData extends XmlCompletionData {
  private static CompletionData ourStyleCompletionData;
  private boolean myCaseInsensitive;
  private static CompletionData ourScriptCompletionData;

  public HtmlCompletionData() {
    this(true);
  }

  protected HtmlCompletionData(boolean _caseInsensitive) {
    myCaseInsensitive = _caseInsensitive;
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

  protected ElementFilter createTagCompletionFilter() {
    return new ElementFilter() {
      public boolean isAcceptable(Object element, PsiElement context) {
        String name = ((XmlTag)context).getName();
        if (name == null) return true;
        
        if (equalNames(name, "style") ||
            equalNames(name,"script")) {
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

  protected ElementFilter createAttributeCompletion() {
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
    
    return language != null && language.getID().equals("JavaScript");
  }

  private boolean isScriptTag(XmlTag tag) {
    if (tag!=null) {
      String tagName = tag.getName();
      if (tagName == null) return false;
      if (myCaseInsensitive) tagName = tagName.toLowerCase();

      return tagName.equals("script");
    }

    return false;
  }

  private boolean isStyleTag(XmlTag tag) {
    if (tag!=null) {
      String tagName = tag.getName();
      if (tagName == null) return false;
      if (myCaseInsensitive) tagName = tagName.toLowerCase();

      return tagName.equals("style");
    }

    return false;
  }

  public CompletionVariant[] findVariants(PsiElement position, CompletionContext context) {
    CompletionVariant[] variants = super.findVariants(position, context);

    if (ourStyleCompletionData!=null && isStyleContext(position)) {
      final CompletionVariant[] styleVariants = ourStyleCompletionData.findVariants(position, context);

      variants = ArrayUtil.mergeArrays(variants,styleVariants, CompletionVariant.class);
    }

    if (ourScriptCompletionData!=null && isScriptContext(position)) {
      final CompletionVariant[] scriptVariants = ourScriptCompletionData.findVariants(position, context);

      variants = ArrayUtil.mergeArrays(variants,scriptVariants, CompletionVariant.class);
    }

    return variants;
  }

  private boolean isStyleAttributeContext(PsiElement position) {
    XmlAttribute parentOfType = PsiTreeUtil.getParentOfType(position, XmlAttribute.class, false);

    if (parentOfType != null) {
      String name = parentOfType.getName();
      if (myCaseInsensitive) name = name.toLowerCase();
      return "style".equals(name); //name.endsWith("style");
    }
    
    return false;
  }
  private boolean isStyleContext(PsiElement position) {
    if (isStyleAttributeContext(position)) return true;

    return isStyleTag(PsiTreeUtil.getParentOfType(position, XmlTag.class, false));
  }

  public void addKeywordVariants(Set<CompletionVariant> set, CompletionContext context, PsiElement position) {
    super.addKeywordVariants(set, context, position);

    if (ourStyleCompletionData!=null && isStyleContext(position)) {
      ourStyleCompletionData.addKeywordVariants(set, context, position);
    } else if (ourScriptCompletionData!=null && isScriptContext(position)) {
      ourScriptCompletionData.addKeywordVariants(set, context, position);
    }
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

    if (isScriptTag(tag) && ourScriptCompletionData!=null) {
      prefix = ourScriptCompletionData.findPrefix(insertedElement, offset);
    } else if (isStyleTag(tag) && ourStyleCompletionData!=null) {
      prefix = ourStyleCompletionData.findPrefix(insertedElement, offset);
    }

    if (prefix == null) {
      prefix = super.findPrefix(insertedElement, offset);
    }

    return prefix;
  }

  public static void setScriptCompletionData(CompletionData scriptCompletionData) {
    ourScriptCompletionData = scriptCompletionData;
  }
}
