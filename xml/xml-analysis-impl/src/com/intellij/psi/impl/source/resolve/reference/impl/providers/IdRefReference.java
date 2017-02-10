/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.util.XmlDeclareIdInCommentAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * @author peter
 */
public class IdRefReference extends BasicAttributeValueReference {
  private final boolean myIdAttrsOnly;

  public IdRefReference(final PsiElement element, int offset, boolean idAttrsOnly) {
    super(element, offset);
    myIdAttrsOnly = idAttrsOnly;
  }

  public IdRefReference(final PsiElement element) {
    super(element);
    myIdAttrsOnly = false;
  }

  @Nullable
  protected PsiElement getIdValueElement(PsiElement element) {
    if (element instanceof XmlTag) {
      final XmlTag tag = (XmlTag)element;
      XmlAttribute attribute = tag.getAttribute(IdReferenceProvider.ID_ATTR_NAME, null);
      if (!myIdAttrsOnly) {
        if (attribute == null) {
          attribute = tag.getAttribute(IdReferenceProvider.NAME_ATTR_NAME, null);
        }
        if (attribute == null) {
          attribute = tag.getAttribute(IdReferenceProvider.STYLE_ID_ATTR_NAME, null);
        }
      }
      return attribute != null ? attribute.getValueElement() : getImplicitIdRefValueElement(tag);
    }
    else {
      return element;
    }
  }

  @Nullable
  protected String getIdValue(final PsiElement element) {
    if (element instanceof XmlTag) {
      final XmlTag tag = (XmlTag)element;
      String s = tag.getAttributeValue(IdReferenceProvider.ID_ATTR_NAME);
      if (!myIdAttrsOnly) {
        if (s == null) s = tag.getAttributeValue(IdReferenceProvider.NAME_ATTR_NAME);
        if (s == null) s = tag.getAttributeValue(IdReferenceProvider.STYLE_ID_ATTR_NAME);
      }
      return s != null ? s: getImplicitIdRefValue(tag);
    } else if (element instanceof PsiComment) {
      return getImplicitIdValue((PsiComment) element);
    }

    return null;
  }

  @Nullable
  public static XmlAttribute getImplicitIdRefAttr(@NotNull XmlTag tag) {
    for (ImplicitIdRefProvider idRefProvider : Extensions.getExtensions(ImplicitIdRefProvider.EXTENSION_POINT_NAME)) {
      XmlAttribute value = idRefProvider.getIdRefAttribute(tag);
      if (value != null) return value;
    }

    return null;
  }

  @Nullable
  public static XmlAttributeValue getImplicitIdRefValueElement(@NotNull XmlTag tag) {
    for (ImplicitIdRefProvider idRefProvider : Extensions.getExtensions(ImplicitIdRefProvider.EXTENSION_POINT_NAME)) {
      XmlAttribute value = idRefProvider.getIdRefAttribute(tag);
      if (value != null) return value.getValueElement();
    }

    return null;
  }

  @Nullable
  public static String getImplicitIdRefValue(@NotNull XmlTag tag) {
    XmlAttributeValue attribute = getImplicitIdRefValueElement(tag);

    return attribute != null ? attribute.getValue() : null;
  }

  protected static boolean isAcceptableTagType(final XmlTag subTag) {
    return subTag.getAttributeValue(IdReferenceProvider.ID_ATTR_NAME) != null ||
           subTag.getAttributeValue(IdReferenceProvider.FOR_ATTR_NAME) != null || getImplicitIdRefValue(subTag) != null ||
           (subTag.getAttributeValue(IdReferenceProvider.NAME_ATTR_NAME) != null &&
            !subTag.getName().contains(".directive"));
  }

  private static final FileBasedUserDataCache<List<PsiElement>> ourCachedIdsCache = new FileBasedUserDataCache<List<PsiElement>>() {
    private final Key<CachedValue<List<PsiElement>>> ourCachedIdsValueKey = Key.create("my.ids.cached.value");

    @Override
    protected List<PsiElement> doCompute(PsiFile file) {
      final List<PsiElement> result = new ArrayList<>();

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

    @Override
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

  @Override
  @Nullable
  public PsiElement resolve() {
    final PsiElement[] result = new PsiElement[1];
    process(new PsiElementProcessor<PsiElement>() {
      final String canonicalText = getCanonicalText();

      @Override
      public boolean execute(@NotNull final PsiElement element) {
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

  @Override
  @NotNull
  public Object[] getVariants() {
    final List<String> result = new LinkedList<>();

    process(new PsiElementProcessor<PsiElement>() {
      @Override
      public boolean execute(@NotNull final PsiElement element) {
        String value = getIdValue(element);
        if (value != null) {
          result.add(value);
        }
        return true;
      }
    });

    return ArrayUtil.toObjectArray(result);
  }

  @Override
  public boolean isSoft() {
    return false;
  }

}
