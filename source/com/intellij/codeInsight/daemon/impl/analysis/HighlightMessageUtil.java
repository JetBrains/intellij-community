
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.psi.*;
import com.intellij.psi.jsp.JspDeclaration;
import com.intellij.psi.util.PsiFormatUtil;

public class HighlightMessageUtil {
  public static String getSymbolName(PsiElement symbol, PsiSubstitutor substitutor) {
    String symbolName = null;
    if (symbol instanceof PsiClass) {
      if (symbol instanceof PsiAnonymousClass){
        symbolName = "anonymous class";
      }
      else{
        symbolName = ((PsiClass)symbol).getQualifiedName();
        if (symbolName == null) {
          symbolName = ((PsiClass)symbol).getName();
        }
      }
    }
    else if (symbol instanceof PsiMethod) {
      symbolName = PsiFormatUtil.formatMethod((PsiMethod)symbol,
          substitutor, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
          PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_FQ_CLASS_NAMES
      );
    }
    else if (symbol instanceof PsiVariable) {
      symbolName = ((PsiVariable)symbol).getName();
    }
    else if (symbol instanceof PsiPackage) {
      symbolName = ((PsiPackage)symbol).getQualifiedName();
    }
    else if (symbol instanceof JspDeclaration) {
      symbolName = symbol.getContainingFile().getName();
    }
    else if (symbol instanceof PsiJavaFile) {
      PsiDirectory directory = ((PsiJavaFile) symbol).getContainingDirectory();
      final PsiPackage aPackage = directory == null ? null : directory.getPackage();
      symbolName = aPackage == null ? null : aPackage.getQualifiedName();
    }
    else if (symbol instanceof PsiDirectory){
      symbolName = ((PsiDirectory) symbol).getName();
    }
    return symbolName;
  }
}