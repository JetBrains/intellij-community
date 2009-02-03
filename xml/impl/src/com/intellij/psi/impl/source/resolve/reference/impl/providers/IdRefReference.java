/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlComment;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.util.XmlDeclareIdInCommentAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;

/**
 * @author peter
 */
public class IdRefReference extends BasicAttributeValueReference {

  public IdRefReference(final PsiElement element, int offset) {
    super(element, offset);
  }

  public IdRefReference(final PsiElement element) {
    super(element);
  }

  @Nullable
  protected static PsiElement getIdValueElement(PsiElement element) {
    if (element instanceof XmlTag) {
      final XmlTag tag = (XmlTag)element;
      XmlAttribute attribute = tag.getAttribute(IdReferenceProvider.ID_ATTR_NAME, null);
      if (attribute == null) {
        attribute = tag.getAttribute(IdReferenceProvider.NAME_ATTR_NAME, null);
      }
      if (attribute == null) {
        attribute = tag.getAttribute(IdReferenceProvider.STYLE_ID_ATTR_NAME, null);
      }
      return attribute != null ? attribute.getValueElement() : null;
    }
    else {
      return element;
    }
  }

  @Nullable
  protected static String getIdValue(final PsiElement element) {
    if (element instanceof XmlTag) {
      final XmlTag tag = (XmlTag)element;
      String s = tag.getAttributeValue(IdReferenceProvider.ID_ATTR_NAME);
      if (s == null) s = tag.getAttributeValue(IdReferenceProvider.NAME_ATTR_NAME);
      if (s == null) s = tag.getAttributeValue(IdReferenceProvider.STYLE_ID_ATTR_NAME);
      return s;
    } else if (element instanceof PsiComment) {
      return getImplicitIdValue((PsiComment) element);
    }

    return null;
  }

  protected static boolean isAcceptableTagType(final XmlTag subTag) {
    return subTag.getAttributeValue(IdReferenceProvider.ID_ATTR_NAME) != null ||
           (subTag.getAttributeValue(IdReferenceProvider.NAME_ATTR_NAME) != null &&
            subTag.getName().indexOf(".directive") == -1);
  }

  private static final FileBasedUserDataCache<List<PsiElement>> ourCachedIdsCache = new FileBasedUserDataCache<List<PsiElement>>() {
    private final Key<CachedValue<List<PsiElement>>> ourCachedIdsValueKey = Key.create("my.ids.cached.value");

    protected List<PsiElement> doCompute(PsiFile file) {
      final List<PsiElement> result = new ArrayList<PsiElement>();

      file.accept(new XmlRecursiveElementVisitor(true) {
        @Override
        public void visitXmlTag(XmlTag tag) {
          if (isAcceptableTagType(tag)) result.add(tag);
          super.visitXmlTag(tag);
        }

        @Override
        public void visitComment(final PsiComment comment) {
          if (isDeclarationComment(comment)) result.add(comment);

          super.visitComment(comment);
        }

        @Override
        public void visitXmlComment(final XmlComment comment) {
          if (isDeclarationComment(comment)) result.add(comment);

          super.visitComment(comment);
        }
      });
      return result;
    }

    protected Key<CachedValue<List<PsiElement>>> getKey() {
      return ourCachedIdsValueKey;
    }
  };

  private static boolean isDeclarationComment(@NotNull final PsiComment comment) {
    return comment.getText().contains("@declare id=");
  }

  @Nullable
  private static String getImplicitIdValue(@NotNull final PsiComment comment) {
    return XmlDeclareIdInCommentAction.getImplicitlyDeclaredId(comment);
  }

  private void process(PsiElementProcessor<PsiElement> processor) {
    final PsiFile psiFile = getElement().getContainingFile();
    process(processor, psiFile);
  }

  public static void process(final PsiElementProcessor<PsiElement> processor, PsiFile file) {
    for (PsiElement e : ourCachedIdsCache.compute(file)) {
      if (!processor.execute(e)) return;
    }
  }

  @Nullable
  public PsiElement resolve() {
    final PsiElement[] result = new PsiElement[1];
    process(new PsiElementProcessor<PsiElement>() {
      String canonicalText = getCanonicalText();

      public boolean execute(final PsiElement element) {
        final String idValue = getIdValue(element);
        if (idValue != null && idValue.equals(canonicalText)) {
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

    process(new PsiElementProcessor<PsiElement>() {
      public boolean execute(final PsiElement element) {
        result.add(getIdValue(element));
        return true;
      }
    });

    return ArrayUtil.toObjectArray(result);
  }

  public boolean isSoft() {
    return false;
  }

}
