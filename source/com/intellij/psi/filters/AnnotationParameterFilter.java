/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.filters;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameValuePair;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public class AnnotationParameterFilter implements ElementFilter{
  private final Class<? extends PsiElement> myClass;
  @NonNls private final String myParameterName;
  private final String myAnnotationQualifiedName;


  public AnnotationParameterFilter(final Class<? extends PsiElement> elementClass,
                                   final String annotationQualifiedName,
                                   @NonNls final String parameterName) {
    myAnnotationQualifiedName = annotationQualifiedName;
    myClass = elementClass;
    myParameterName = parameterName;
  }

  public boolean isAcceptable(Object element, PsiElement context) {
    final PsiElement parent = ((PsiElement)element).getParent();
    if (parent instanceof PsiNameValuePair) {
      final PsiNameValuePair pair = (PsiNameValuePair)parent;
      final String name = pair.getName();
      if (myParameterName.equals(name) || name == null && "value".equals(myParameterName)) {
        final PsiElement psiElement = pair.getParent();
        if (psiElement instanceof PsiAnnotationParameterList) {
          final PsiElement grandParent = psiElement.getParent();
          if (grandParent instanceof PsiAnnotation) {
            if (myAnnotationQualifiedName.equals(((PsiAnnotation)grandParent).getQualifiedName())) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  public boolean isClassAcceptable(Class hintClass) {
    return myClass.isAssignableFrom(hintClass);
  }
}
