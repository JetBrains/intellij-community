/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

/**
 * @author peter
*/
public class IdRefReference extends JspReferencesProvider.BasicAttributeValueReference {

  public IdRefReference(final PsiElement element, int offset) {
    super(element, offset);
  }

  protected PsiElement getIdValueElement(XmlTag tag) {
    return tag.getAttribute(IdReferenceProvider.ID_ATTR_NAME, null).getValueElement();
  }

  protected String getIdValue(final XmlTag subTag) {
    return subTag.getAttributeValue(IdReferenceProvider.ID_ATTR_NAME);
  }

  protected boolean isAcceptableTagType(final XmlTag subTag) {
    return subTag.getAttributeValue(IdReferenceProvider.ID_ATTR_NAME) != null;
  }

  private static Key<CachedValue<List<XmlTag>>> ourBeansCachedValueKey = Key.create("my.beans.cached.value");

  private void process(PsiElementProcessor<XmlTag> processor) {
    final PsiFile psiFile = getElement().getContainingFile();
    CachedValue<List<XmlTag>> value = psiFile.getUserData(ourBeansCachedValueKey);

    if (value == null) {
      value = psiFile.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<List<XmlTag>>() {
        public Result<List<XmlTag>> compute() {
          final List<XmlTag> result = new LinkedList<XmlTag>();

          psiFile.acceptChildren(new PsiRecursiveElementVisitor() {
            public void visitXmlTag(XmlTag tag) {
              if (isAcceptableTagType(tag)) result.add(tag);
              super.visitXmlTag(tag);
            }
          });
          return new Result<List<XmlTag>>(result, psiFile);
        }
      }, false);
      psiFile.putUserData(ourBeansCachedValueKey, value);
    }

    for (XmlTag tag : value.getValue()) {
      if (!processor.execute(tag)) return;
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
