/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataCache;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.impl.source.jsp.jspJava.JspDirective;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

/**
 * @author peter
*/
public class IdRefReference extends BasicAttributeValueReference {

  public IdRefReference(final PsiElement element, int offset) {
    super(element, offset);
  }

  protected static PsiElement getIdValueElement(XmlTag tag) {
    XmlAttribute attribute = tag.getAttribute(IdReferenceProvider.ID_ATTR_NAME, null);
    if (attribute == null) {
      attribute = tag.getAttribute(IdReferenceProvider.NAME_ATTR_NAME, null);
    }
    if (attribute == null) {
      attribute = tag.getAttribute(IdReferenceProvider.STYLE_ID_ATTR_NAME, null);
    }
    return attribute.getValueElement();
  }

  protected static String getIdValue(final XmlTag subTag) {
    String s = subTag.getAttributeValue(IdReferenceProvider.ID_ATTR_NAME);
    if (s == null) s = subTag.getAttributeValue(IdReferenceProvider.NAME_ATTR_NAME);
    if (s == null) s = subTag.getAttributeValue(IdReferenceProvider.STYLE_ID_ATTR_NAME);
    return s;
  }

  protected static boolean isAcceptableTagType(final XmlTag subTag) {
    return subTag.getAttributeValue(IdReferenceProvider.ID_ATTR_NAME) != null ||
      ( subTag.getAttributeValue(IdReferenceProvider.NAME_ATTR_NAME) != null &&
        !(subTag instanceof JspDirective) &&
        subTag.getName().indexOf(".directive") == -1
      );
  }

  private static Key<CachedValue<List<XmlTag>>> ourCachedIdsValueKey = Key.create("my.ids.cached.value");
  private static UserDataCache<CachedValue<List<XmlTag>>, PsiFile, Object> ourCachedIdsCache = new UserDataCache<CachedValue<List<XmlTag>>, PsiFile, Object>() {
    protected CachedValue<List<XmlTag>> compute(final PsiFile xmlFile, final Object o) {
      return xmlFile.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<List<XmlTag>>() {
        public Result<List<XmlTag>> compute() {
          final List<XmlTag> result = new LinkedList<XmlTag>();

          xmlFile.accept(new PsiRecursiveElementVisitor() {
            public void visitXmlTag(XmlTag tag) {
              if (isAcceptableTagType(tag)) result.add(tag);
              super.visitXmlTag(tag);
            }
          });
          return new Result<List<XmlTag>>(result, xmlFile);
        }
      }, false);
    }
  };

  private void process(PsiElementProcessor<XmlTag> processor) {
    final PsiFile psiFile = getElement().getContainingFile();
    process(processor, psiFile);
  }

  public static void process(final PsiElementProcessor<XmlTag> processor, PsiFile psiFile) {
    final FileViewProvider fileViewProvider = psiFile.getViewProvider();
    psiFile = fileViewProvider.getPsi(fileViewProvider.getBaseLanguage());
    psiFile.getFirstChild(); // expand chameleon out of lock

    for(XmlTag t:ourCachedIdsCache.get(ourCachedIdsValueKey, psiFile, null).getValue()) {
      if (!processor.execute(t)) return;
    }
  }

  @Nullable
  public PsiElement resolve() {
    final PsiElement[] result = new PsiElement[1];
    process(new PsiElementProcessor<XmlTag>() {
      String canonicalText = getCanonicalText();

      public boolean execute(final XmlTag element) {
        if (getIdValue(element).equals(canonicalText)) {
          result[0] = getIdValueElement(element);
          return false;
        }
        return true;
      }
    });

    return result[0];
  }

  public Object[] getVariants() {
    final List<String> result = new LinkedList<String>();

    process(new PsiElementProcessor<XmlTag>() {
      public boolean execute(final XmlTag element) {
        result.add(getIdValue(element));
        return true;
      }
    });

    return result.toArray(new Object[result.size()]);
  }

  public boolean isSoft() {
    return false;
  }
}
