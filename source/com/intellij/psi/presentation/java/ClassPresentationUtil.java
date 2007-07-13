/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.psi.presentation.java;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

import javax.swing.*;

public class ClassPresentationUtil {
  public static String getNameForClass(PsiClass aClass, boolean qualified) {
    if (aClass instanceof PsiAnonymousClass) {
      return PsiBundle.message("anonymous.class.context.display", getContextName(aClass, qualified));
    }
    else {
      if (qualified){
        String qName = aClass.getQualifiedName();
        if (qName != null) return qName;
      }

      String className = aClass.getName();
      String contextName = getContextName(aClass, qualified);
      return contextName != null ? PsiBundle.message("class.context.display", className, contextName) : className;
    }
  }

  private static String getNameForElement(PsiElement element, boolean qualified) {
    if (element instanceof PsiClass){
      return getNameForClass((PsiClass)element, qualified);
    }
    else if (element instanceof PsiMethod){
      PsiMethod method = (PsiMethod)element;
      String methodName = method.getName();
      return PsiBundle.message("method.context.display", methodName, getContextName(method, qualified));
    }
    else if (element instanceof PsiJavaFile){
      return null;
    }
    else if (element instanceof PsiFile){
      return ((PsiFile)element).getName();
    }
    else{
      return null;
    }
  }

  private static String getContextName(PsiElement element, boolean qualified) {
    PsiElement parent = PsiTreeUtil.getParentOfType(element, PsiMember.class, PsiFile.class);
    while(true){
      String name = getNameForElement(parent, qualified);
      if (name != null) return name;
      if (parent instanceof PsiFile || parent == null) return null;
      parent = parent.getParent();
    }
  }

  public static ItemPresentation getPresentation(final PsiClass psiClass) {
    if (psiClass instanceof PsiAnonymousClass) return null;
    return new ItemPresentation() {
      public String getPresentableText() {
        return getNameForClass(psiClass, false);
      }

      public String getLocationString() {
        PsiFile file = psiClass.getContainingFile();
        if (file instanceof PsiJavaFile) {
          PsiJavaFile javaFile = (PsiJavaFile)file;
          String packageName = javaFile.getPackageName();
          if (packageName.length() == 0) return null;
          return "(" + packageName + ")";
        }
        return null;
      }

      public TextAttributesKey getTextAttributesKey() {
        if (psiClass.isDeprecated()) {
          return CodeInsightColors.DEPRECATED_ATTRIBUTES;
        }
        return null;
      }

      public Icon getIcon(boolean open) {
        return psiClass.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
      }
    };
  }
}