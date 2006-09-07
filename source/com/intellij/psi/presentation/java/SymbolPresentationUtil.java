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

import com.intellij.lang.properties.psi.Property;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;

import javax.swing.*;

public class SymbolPresentationUtil {
  private SymbolPresentationUtil() {
  }

  public static String getSymbolPresentableText(PsiElement element) {
    if (element instanceof PsiMethod){
      return PsiFormatUtil.formatMethod(
        (PsiMethod)element,
        PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
        PsiFormatUtil.SHOW_TYPE
      );
    }

    if (element instanceof NavigationItem &&
        !(element instanceof PsiMember)
       ){
      final ItemPresentation presentation = ((NavigationItem)element).getPresentation();
      if (presentation != null){
        return presentation.getPresentableText();
      }
    }

    if (element instanceof PsiNamedElement) return ((PsiNamedElement)element).getName();
    return element.getText();
  }

  public static String getSymbolContainerText(PsiElement element) {
    String result = null;

    if (element instanceof Property) {
      result = element.getContainingFile().getName();
    }
    else {
      PsiElement container = PsiTreeUtil.getParentOfType(element, PsiMember.class, PsiFile.class);

      if (container instanceof PsiClass) {
        String qName = ((PsiClass)container).getQualifiedName();
        if (qName != null) {
          result = qName;
        }
        else {
          result = ((PsiClass)container).getName();
        }
      }
      else if (container instanceof PsiJavaFile) {
        result = ((PsiJavaFile)container).getPackageName();
      }
      else {//TODO: local classes
        result = null;
      }
    }

    if (result == null &&
        element instanceof NavigationItem &&
        !(element instanceof PsiMember) // to prevent recursion
       ){
      final ItemPresentation presentation = ((NavigationItem)element).getPresentation();
      if (presentation != null){
        result = presentation.getLocationString();
      }
    }

    if (result == null || result.trim().length() == 0) return null;
    return PsiBundle.message("aux.context.display", result);
  }

  public static ItemPresentation getMethodPresentation(final PsiMethod psiMethod) {
    return new ItemPresentation() {
      public String getPresentableText() {
        return getSymbolPresentableText(psiMethod);
      }

      public TextAttributesKey getTextAttributesKey() {
        return null;
      }

      public String getLocationString() {
        return getSymbolContainerText(psiMethod);
      }

      public Icon getIcon(boolean open) {
        return psiMethod.getIcon(Iconable.ICON_FLAG_VISIBILITY);
      }
    };
  }

  public static ItemPresentation getFieldPresentation(final PsiField psiField) {
    return new ItemPresentation() {
      public String getPresentableText() {
        return getSymbolPresentableText(psiField);
      }

      public TextAttributesKey getTextAttributesKey() {
        return null;
      }

      public String getLocationString() {
        return getSymbolContainerText(psiField);
      }

      public Icon getIcon(boolean open) {
        return psiField.getIcon(Iconable.ICON_FLAG_VISIBILITY);
      }
    };
  }

  public static ItemPresentation getVariablePresentation(final PsiVariable variable) {
    return new ItemPresentation() {
      public String getPresentableText() {
        return PsiFormatUtil.formatVariable(variable, PsiFormatUtil.SHOW_TYPE, PsiSubstitutor.EMPTY);
      }

      public String getLocationString() {
        return "";
      }

      public TextAttributesKey getTextAttributesKey() {
        return null;
      }

      public Icon getIcon(boolean open) {
        return variable.getIcon(Iconable.ICON_FLAG_OPEN);
      }
    };
  }
}