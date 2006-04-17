/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class GenericValueReferenceProvider implements PsiReferenceProvider {
  @NotNull
  public GenericReference[] getReferencesByElement(PsiElement element) {
    if (!(element instanceof XmlTag || element instanceof XmlAttribute)) return GenericReference.EMPTY_ARRAY;
    PsiElement originalElement = element.getUserData(PsiUtil.ORIGINAL_KEY);
    if (originalElement != null){
      element = originalElement;
    }
    final Module module = ModuleUtil.findModuleForPsiElement(element);
    if (module == null) return GenericReference.EMPTY_ARRAY;

    final XmlTag tag = element instanceof XmlTag ? (XmlTag) element : ((XmlAttribute) element).getParent();
    if (element == tag && tag.getValue().getTextElements().length ==0) return GenericReference.EMPTY_ARRAY;

    final DomElement domElement = DomManager.getDomManager(module.getProject()).getDomElement(tag);
    if (!(domElement instanceof GenericDomValue)) return GenericReference.EMPTY_ARRAY;

    final Class parameter = DomUtil.getGenericValueType(domElement.getDomElementType());
    if (PsiType.class.isAssignableFrom(parameter)) {
      return new GenericReference[]{new PsiTypeReference(this, (GenericDomValue<PsiType>)domElement)};
    }
    if (PsiClass.class.isAssignableFrom(parameter)) {
      return new GenericReference[]{new PsiClassReference(this, (GenericDomValue<PsiClass>)domElement)};
    }
    if (Integer.class.isAssignableFrom(parameter)) {
      return new GenericReference[]{new GenericDomValueReference(this, (GenericDomValue) domElement) {
        public Object[] getVariants() {
          return new Object[]{"239", "42"};
        }

        public PsiElement resolveInner() {
          return getValueElement();
        }
      }
      };
    }
    if (Enum.class.isAssignableFrom(parameter)) {
      return new GenericReference[]{new GenericDomValueReference(this, (GenericDomValue) domElement) {
        public Object[] getVariants() {
          final Enum[] enumConstants = (Enum[])parameter.getEnumConstants();
          return ContainerUtil.map2Array(enumConstants, String.class, new Function<Enum, String>() {
            public String fun(final Enum s) {
              return NamedEnumUtil.getEnumValueByElement(s);
            }
          });
        }

        public PsiElement resolveInner() {
          return getValueElement();
        }
      }
      };
    }
    if (Boolean.class.isAssignableFrom(parameter)) {
      return new GenericReference[]{new GenericDomValueReference(this, (GenericDomValue) domElement) {
        public Object[] getVariants() {
          return new Object[]{"true", "false"};
        }

        public PsiElement resolveInner() {
          return getValueElement();
        }
      }
      };
    }


    if (!String.class.isAssignableFrom(parameter)) {
      return new GenericReference[]{new GenericDomValueReference(this, (GenericDomValue) domElement)};
    }

    return GenericReference.EMPTY_ARRAY;
  }


  @NotNull
  public GenericReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return getReferencesByElement(element);
  }

  @NotNull
  public GenericReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return getReferencesByElement(position);
  }

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
  }
}
