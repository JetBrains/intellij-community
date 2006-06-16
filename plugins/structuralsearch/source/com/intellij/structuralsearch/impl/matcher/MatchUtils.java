package com.intellij.structuralsearch.impl.matcher;

import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 24.12.2003
 * Time: 22:10:20
 * To change this template use Options | File Templates.
 */
public class MatchUtils {
  public static final String SPECIAL_CHARS = "*(){}[]^$\\.-|";

  public static final boolean compareWithNoDifferenceToPackage(final String typeImage,@NonNls final String typeImage2) {
    if (typeImage == null || typeImage2 == null) return typeImage == typeImage2;
    return typeImage2.endsWith(typeImage) && (
      typeImage.length() == typeImage2.length() ||
      typeImage2.charAt(typeImage2.length()-typeImage.length()-1)=='.' // package separator
    );
  }

  public static PsiElement getReferencedElement(final PsiElement element) {
    if (element instanceof PsiReference) {
      return ((PsiReference)element).resolve();
    }

    if (element instanceof PsiTypeElement) {
      PsiType type = ((PsiTypeElement)element).getType();

      if (type instanceof PsiArrayType) {
        type = ((PsiArrayType)type).getComponentType();
      }
      if (type instanceof PsiClassType) {
        return ((PsiClassType)type).resolve();
      }
      return null;
    }
    return element;
  }
}
