package com.intellij.psi.impl.cache;

import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.impl.compiled.ClsTypeElementImpl;

/**
 * @author max
 */
public class TypeInfo {
  public String text;
  public byte arrayCount;
  public boolean isEllipsis;

  public static TypeInfo create(PsiType type, PsiTypeElement typeElement) {
    if (type == null) return null;

    final boolean isEllipsis = type instanceof PsiEllipsisType;
    int arrayCount = type.getArrayDimensions();


    final String text;
    if (typeElement != null) {
      while (typeElement.getFirstChild() instanceof PsiTypeElement) {
        typeElement = (PsiTypeElement)typeElement.getFirstChild();
      }

      text = typeElement instanceof PsiCompiledElement
             ? ((ClsTypeElementImpl)typeElement).getCanonicalText()
             : typeElement.getText();
    }
    else {
      type = type.getDeepComponentType();
      text = type.getInternalCanonicalText();
    }

    TypeInfo result = new TypeInfo();
    result.text = text;
    result.arrayCount = (byte)arrayCount;
    result.isEllipsis = isEllipsis;

    return result;
  }

  public static TypeInfo fromString(String typeText) {
    TypeInfo info = new TypeInfo();
    if (typeText.endsWith("...")) {
      info.isEllipsis = true;
      typeText = typeText.substring(0, typeText.length() - 3);
    }

    while (typeText.endsWith("[]")) {
      info.arrayCount++;
      typeText = typeText.substring(0, typeText.length() - 2);
    }

    info.text = typeText;

    return info;
  }
}
