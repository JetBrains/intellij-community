package com.intellij.codeInsight.completion;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.util.ArrayUtil;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Oct 13, 2004
 * Time: 6:50:33 PM
 * To change this template use File | Settings | File Templates.
 */
public class HtmlCompletionData extends XmlCompletionData {
  private CompletionData myStyleCompletionData;
  private boolean myCaseInsensitive;
  private CompletionData myScriptCompletionData;

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

  protected ElementFilter createTagCompletionFilter() {
    return new ElementFilter() {
      public boolean isAcceptable(Object element, PsiElement context) {
        String name = ((XmlTag)context).getName();
        if (equalNames(name, "style") ||
            equalNames(name,"script")) {
          return false;
        }

        XmlAttribute parentOfType = PsiTreeUtil.getParentOfType((PsiElement)element,XmlAttribute.class);
        if ( parentOfType != null && equalNames(parentOfType.getName(), "style")) return false;
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
        if (equalNames(((XmlAttribute)context).getName(), "style")) return false;
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
        if (equalNames(((XmlAttribute)context.getParent()).getName(), "style")) return false;
        return true;
      }

      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    };
  }

  private boolean isScriptContext(PsiElement element) {
    final XmlAttribute attribute = PsiTreeUtil.getParentOfType(element, XmlAttribute.class, false);
    if(attribute != null ||
       ((element instanceof XmlToken) && ((XmlToken)element).getTokenType() == XmlTokenType.XML_NAME)) {
      return false; // we could recognize
    }

    XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class, false);

    return isScriptTag(tag);

  }

  private boolean isScriptTag(XmlTag tag) {
    if (tag!=null) {
      String tagName = tag.getName();
      if (myCaseInsensitive) tagName = tagName.toLowerCase();

      return tagName.equals("script");
    }

    return false;
  }

  private boolean isStyleTag(XmlTag tag) {
    if (tag!=null) {
      String tagName = tag.getName();
      if (myCaseInsensitive) tagName = tagName.toLowerCase();

      return tagName.equals("style");
    }

    return false;
  }

  public CompletionVariant[] findVariants(PsiElement position, CompletionContext context) {
    CompletionVariant[] variants = super.findVariants(position, context);

    if (myStyleCompletionData!=null && isStyleContext(position)) {
      final CompletionVariant[] styleVariants = myStyleCompletionData.findVariants(position, context);

      variants = ArrayUtil.mergeArrays(variants,styleVariants, CompletionVariant.class);
    }

    if (myScriptCompletionData!=null && isScriptContext(position)) {
      final CompletionVariant[] scriptVariants = myScriptCompletionData.findVariants(position, context);

      variants = ArrayUtil.mergeArrays(variants,scriptVariants, CompletionVariant.class);
    }

    return variants;
  }

  private boolean isStyleContext(PsiElement position) {
    XmlAttribute parentOfType = PsiTreeUtil.getParentOfType(position, XmlAttribute.class, false);

    if (parentOfType != null) {
      String name = parentOfType.getName();
      if (myCaseInsensitive) name = name.toLowerCase();
      return name.equals("style");
    }

    return isStyleTag(PsiTreeUtil.getParentOfType(position, XmlTag.class, false));
  }

  public void addKeywordVariants(Set set, CompletionContext context, PsiElement position) {
    super.addKeywordVariants(set, context, position);

    if (myStyleCompletionData!=null && isStyleContext(position)) {
      myStyleCompletionData.addKeywordVariants(set, context, position);
    } else if (myScriptCompletionData!=null && isScriptContext(position)) {
      myScriptCompletionData.addKeywordVariants(set, context, position);
    }
  }

  public void setStyleCompletionData(CompletionData cssCompletionData) {
    myStyleCompletionData = cssCompletionData;
  }

  public void registerVariant(CompletionVariant variant) {
    super.registerVariant(variant);
    if (isCaseInsensitive()) variant.setCaseInsensitive(true);
  }

  public String findPrefix(PsiElement insertedElement, int offset) {
    XmlTag tag = PsiTreeUtil.getParentOfType(insertedElement, XmlTag.class, false);
    String prefix = null;

    if (isScriptTag(tag) && myScriptCompletionData!=null) {
      prefix = myScriptCompletionData.findPrefix(insertedElement, offset);
    } else if (isStyleTag(tag) && myStyleCompletionData!=null) {
      prefix = myStyleCompletionData.findPrefix(insertedElement, offset);
    }

    if (prefix == null) {
      prefix = super.findPrefix(insertedElement, offset);
    }

    return prefix;
  }

  public void setScriptCompletionData(CompletionData scriptCompletionData) {
    myScriptCompletionData = scriptCompletionData;
  }
}
